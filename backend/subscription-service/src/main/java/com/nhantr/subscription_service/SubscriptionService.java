package com.nhantr.subscription_service;

import com.nhantr.common.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SandboxPaymentProvider provider;
    public SubscriptionService(JdbcTemplate jdbc,TransactionTemplate tx,Clock clock,SandboxPaymentProvider provider) {
        this.jdbc=jdbc; this.tx=tx; this.clock=clock; this.provider=provider;
    }
    public List<Plan> plans() {
        return List.of(new Plan("FREE","Free","Miễn phí",true,false,List.of("Chia sẻ ảnh và trò chuyện")),
                new Plan("PLUS","Plus","Sandbox · không thu tiền thật",true,provider.enabled(),List.of("Tạo ảnh AI mô phỏng","Quản lý quyền lợi trên server")));
    }
    public SubscriptionState.Snapshot snapshot(Long userId) {
        return tx.execute(s -> { var state=lock(userId); expirePending(userId); return lock(userId).snapshot(clock.instant()); });
    }
    public Grant entitlements(Long userId) {
        var snapshot=snapshot(userId);
        return new Grant(snapshot.entitlements().aiGeneration(),snapshot.validUntil());
    }
    public void cancel(Long userId,boolean cancel) {
        tx.executeWithoutResult(s -> { var state=lock(userId); state.cancel(cancel,clock.instant()); save(state); });
    }
    public void override(Long actor,Long userId,String tier,Instant end,String note) {
        if (note==null || note.isBlank() || note.length()>1000 || !List.of("FREE","PLUS").contains(tier)
                || ("PLUS".equals(tier) && (end==null || !end.isAfter(clock.instant())))) {
            throw ApiException.badRequest("INVALID_OVERRIDE","A valid tier, future PLUS expiry and audit note are required.");
        }
        tx.executeWithoutResult(s -> {
            var state=lock(userId); String before=state.snapshot(clock.instant()).tier();
            state.grantSessionId=null;
            if ("PLUS".equals(tier)) state.grant(end,clock.instant()); else state.revoke(clock.instant());
            save(state); audit(state,actor,"ADMIN_OVERRIDE",before,note.trim());
        });
    }
    public Checkout checkout(Long userId,String tier,UUID key) {
        if(!provider.enabled()) throw ApiException.forbidden("SANDBOX_DISABLED","Sandbox checkout is disabled.");
        if (!"PLUS".equals(tier) || key==null) throw ApiException.badRequest("INVALID_CHECKOUT","PLUS and clientRequestId are required.");
        return tx.execute(s -> {
            var state=lock(userId); expirePending(userId);
            var replay=jdbc.query("SELECT c.* FROM subscription_checkout_keys k JOIN subscription_checkouts c ON k.session_id=c.session_id WHERE k.user_id=? AND k.client_request_id=?",
                    (rs,n) -> checkoutFrom(rs),userId,key);
            if (!replay.isEmpty()) return replay.getFirst();
            var pending=jdbc.query("SELECT * FROM subscription_checkouts WHERE user_id=? AND status='PENDING'",(rs,n) -> checkoutFrom(rs),userId);
            Checkout result;
            if (pending.isEmpty()) {
                String id=UUID.randomUUID().toString(); Instant now=clock.instant(),end=now.plusSeconds(1800);
                jdbc.update("INSERT INTO subscription_checkouts(session_id,user_id,tier,created_at,expires_at) VALUES (?,?,?,?,?)",id,userId,tier,ts(now),ts(end));
                result=new Checkout(id,provider.checkoutUrl(id),true,end);
                state.payment(id,"PENDING"); save(state);
            } else result=pending.getFirst();
            jdbc.update("INSERT INTO subscription_checkout_keys(user_id,client_request_id,tier,session_id) VALUES (?,?,?,?)",userId,key,tier,result.sessionId());
            return result;
        });
    }
    public Checkout sandboxCheckout(String sessionId) {
        return jdbc.query("SELECT * FROM subscription_checkouts WHERE session_id=?",(rs,n) -> checkoutFrom(rs),sessionId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("CHECKOUT_NOT_FOUND","Checkout not found."));
    }
    public boolean applyEvent(PaymentEvent event) {
        if(event!=null) event=new PaymentEvent(event.eventId(),event.sessionId(),event.type(),canonical(event.occurredAt()),canonical(event.periodEnd()));
        validate(event);
        final PaymentEvent normalized=event;
        return applyNormalizedEvent(normalized);
    }
    private boolean applyNormalizedEvent(PaymentEvent event) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            List<Long> users=jdbc.query("SELECT user_id FROM subscription_checkouts WHERE session_id=?",(rs,n) -> rs.getLong(1),event.sessionId());
            if (users.isEmpty()) throw ApiException.notFound("CHECKOUT_NOT_FOUND","Checkout not found.");
            var state=lock(users.getFirst());
            var prior=jdbc.query("SELECT * FROM subscription_events WHERE event_id=?",(rs,n) -> new PaymentEvent(rs.getString("event_id"),rs.getString("session_id"),rs.getString("event_type"),instant(rs,"occurred_at"),instant(rs,"period_end")),event.eventId());
            if (!prior.isEmpty()) {
                if (!prior.getFirst().equals(event)) throw ApiException.conflict("PAYMENT_EVENT_CONFLICT","Event identity was reused with another payload.");
                return false;
            }
            // Event identity is unique globally; account lock serializes all transitions for one owner.
            int inserted=jdbc.update("INSERT INTO subscription_events(event_id,session_id,event_type,occurred_at,period_end) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                    event.eventId(),event.sessionId(),event.type(),ts(event.occurredAt()),ts(event.periodEnd()));
            if (inserted==0) throw ApiException.conflict("PAYMENT_EVENT_CONFLICT","Event identity is already used.");
            var row=jdbc.queryForMap("SELECT * FROM subscription_checkouts WHERE session_id=? FOR UPDATE",event.sessionId());
            String previous=(String)row.get("status");
            Instant last=row.get("event_at")==null?null:((java.sql.Timestamp)row.get("event_at")).toInstant();
            boolean current=event.sessionId().equals(state.paymentSessionId);
            // Expired/failed/refunded sessions are terminal; no delayed success can resurrect them.
            boolean grantSource=event.sessionId().equals(state.grantSessionId);
            boolean revocation=List.of("REFUNDED","CANCELED").contains(event.type());
            if ((!current && !(grantSource && revocation)) || (last!=null && !event.occurredAt().isAfter(last)) || List.of("FAILED","REFUNDED","EXPIRED","CANCELED").contains(previous)) return false;
            if ("SUCCEEDED".equals(previous) && !List.of("REFUNDED","CANCELED").contains(event.type())) return false;
            Instant expires=((java.sql.Timestamp)row.get("expires_at")).toInstant();
            if ("SUCCEEDED".equals(event.type()) && !event.occurredAt().isBefore(expires)) return false;
            String before=state.snapshot(clock.instant()).tier();
            switch(event.type()) {
                case "SUCCEEDED" -> { state.grant(event.periodEnd(),event.occurredAt()); state.grantSessionId=event.sessionId(); state.payment(event.sessionId(),"SUCCEEDED"); }
                case "FAILED" -> { state.payment(event.sessionId(),"FAILED");
                    if(state.snapshot(clock.instant()).entitlements().aiGeneration()) state.status="GRACE"; }
                case "REFUNDED", "CANCELED" -> { if(grantSource) { state.revoke(event.occurredAt()); state.grantSessionId=null; } if(current) state.payment(event.sessionId(),"FAILED"); }
                default -> throw ApiException.badRequest("INVALID_PAYMENT_EVENT","Unknown payment type.");
            }
            jdbc.update("UPDATE subscription_checkouts SET status=?,event_at=?,granted=? WHERE session_id=?",event.type(),ts(event.occurredAt()),"SUCCEEDED".equals(event.type()),event.sessionId());
            save(state); audit(state,null,"PAYMENT_"+event.type(),before,"Verified sandbox event "+event.eventId());
            return true;
        }));
    }
    private void validate(PaymentEvent e) {
        Instant now=clock.instant();
        if (e==null || e.eventId()==null || !e.eventId().matches("[A-Za-z0-9_-]{1,128}") || e.sessionId()==null
                || e.occurredAt()==null || e.occurredAt().isAfter(now.plusSeconds(300)) || e.type()==null
                || !List.of("SUCCEEDED","FAILED","REFUNDED","CANCELED").contains(e.type())
                || ("SUCCEEDED".equals(e.type()) && (e.periodEnd()==null || !e.periodEnd().isAfter(e.occurredAt())
                    || e.periodEnd().isAfter(e.occurredAt().plus(366,ChronoUnit.DAYS))))) {
            throw ApiException.badRequest("INVALID_PAYMENT_EVENT","Payment event is invalid.");
        }
    }
    private SubscriptionState lock(Long userId) {
        if (userId==null || userId<=0) throw ApiException.badRequest("INVALID_ACCOUNT","A positive user identity is required.");
        jdbc.update("INSERT INTO subscription_accounts(user_id) VALUES (?) ON CONFLICT DO NOTHING",userId);
        return jdbc.queryForObject("SELECT * FROM subscription_accounts WHERE user_id=? FOR UPDATE",(rs,n) -> {
            var state=SubscriptionState.free(userId); state.tier=rs.getString("tier"); state.status=rs.getString("status");
            state.grantSessionId=rs.getString("grant_session_id");
            state.paymentStatus=rs.getString("payment_status"); state.paymentSessionId=rs.getString("payment_session_id");
            state.cancelAtPeriodEnd=rs.getBoolean("cancel_at_period_end"); state.currentPeriodEnd=instant(rs,"current_period_end");
            state.effectiveAt=instant(rs,"effective_at"); return state;
        },userId);
    }
    private void expirePending(Long userId) {
        var expired=jdbc.query("UPDATE subscription_checkouts SET status='EXPIRED' WHERE user_id=? AND status='PENDING' AND expires_at<=? RETURNING session_id",(rs,n) -> rs.getString(1),userId,ts(clock.instant()));
        for(String id:expired) jdbc.update("UPDATE subscription_accounts SET payment_status='FAILED' WHERE user_id=? AND payment_session_id=?",userId,id);
    }
    private void save(SubscriptionState s) {
        jdbc.update("UPDATE subscription_accounts SET tier=?,status=?,payment_status=?,payment_session_id=?,grant_session_id=?,cancel_at_period_end=?,current_period_end=?,effective_at=? WHERE user_id=?",
                s.tier,s.status,s.paymentStatus,s.paymentSessionId,s.grantSessionId,s.cancelAtPeriodEnd,ts(s.currentPeriodEnd),ts(s.effectiveAt),s.userId);
    }
    private void audit(SubscriptionState s,Long actor,String action,String before,String note) {
        jdbc.update("INSERT INTO subscription_audit(user_id,actor_id,action,before_tier,after_tier,effective_at,note) VALUES (?,?,?,?,?,?,?)",
                s.userId,actor,action,before,s.tier,ts(s.effectiveAt==null?clock.instant():s.effectiveAt),note);
    }
    private Checkout checkoutFrom(ResultSet rs) throws SQLException {
        String id=rs.getString("session_id"); return new Checkout(id,provider.checkoutUrl(id),true,instant(rs,"expires_at"));
    }
    private static Instant canonical(Instant i) { return i==null?null:i.truncatedTo(ChronoUnit.MICROS); }
    private static Timestamp ts(Instant i) { return i==null?null:Timestamp.from(i); }
    private static Instant instant(ResultSet rs,String column) throws SQLException { Timestamp t=rs.getTimestamp(column); return t==null?null:t.toInstant(); }
    public record Plan(String tier,String name,String priceLabel,boolean sandbox,boolean checkoutEnabled,List<String> benefits) {}
    public record Grant(boolean aiGeneration,Instant validUntil) {}
    public record Checkout(String sessionId,String url,boolean sandbox,Instant expiresAt) {}
    public record PaymentEvent(String eventId,String sessionId,String type,Instant occurredAt,Instant periodEnd) {}
}
