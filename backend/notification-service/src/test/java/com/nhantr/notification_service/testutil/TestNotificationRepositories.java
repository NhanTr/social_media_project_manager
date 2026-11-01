package com.nhantr.notification_service.testutil;

import com.nhantr.notification_service.entity.DeviceToken;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.entity.NotificationDelivery;
import com.nhantr.notification_service.repository.DeviceTokenRepository;
import com.nhantr.notification_service.repository.NotificationDeliveryRepository;
import com.nhantr.notification_service.repository.NotificationRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class TestNotificationRepositories {

    public static class FakeNotificationRepository implements NotificationRepository {
        private final Map<Long, Notification> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(100);

        @Override
        public Optional<Notification> findByIdAndUserId(Long id, Long userId) {
            Notification n = store.get(id);
            return (n != null && Objects.equals(n.getUserId(), userId)) ? Optional.of(n) : Optional.empty();
        }

        @Override
        public Optional<Notification> findByIdempotencyKey(String idempotencyKey) {
            if (idempotencyKey == null) return Optional.empty();
            return store.values().stream()
                    .filter(n -> idempotencyKey.equals(getNotificationIdempotencyKey(n)))
                    .findFirst();
        }

        @Override
        public List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable) {
            return store.values().stream()
                    .filter(n -> Objects.equals(n.getUserId(), userId))
                    .sorted((a, b) -> {
                        int c = b.getCreatedAt().compareTo(a.getCreatedAt());
                        return c != 0 ? c : Long.compare(b.getId(), a.getId());
                    })
                    .limit(pageable.getPageSize())
                    .toList();
        }

        @Override
        public List<Notification> pageAfter(Long userId, Instant cursorTime, Long cursorId, Pageable pageable) {
            return store.values().stream()
                    .filter(n -> Objects.equals(n.getUserId(), userId))
                    .filter(n -> n.getCreatedAt().isBefore(cursorTime)
                            || (n.getCreatedAt().equals(cursorTime) && n.getId() < cursorId))
                    .sorted((a, b) -> {
                        int c = b.getCreatedAt().compareTo(a.getCreatedAt());
                        return c != 0 ? c : Long.compare(b.getId(), a.getId());
                    })
                    .limit(pageable.getPageSize())
                    .toList();
        }

        @Override
        public int markAllRead(Long userId, Instant now) {
            int count = 0;
            for (Notification n : store.values()) {
                if (Objects.equals(n.getUserId(), userId) && !n.isRead()) {
                    n.markRead();
                    count++;
                }
            }
            return count;
        }

        @Override
        public long countByUserIdAndReadFalse(Long userId) {
            return store.values().stream()
                    .filter(n -> Objects.equals(n.getUserId(), userId) && !n.isRead())
                    .count();
        }

        @Override
        public <S extends Notification> S save(S entity) {
            try {
                if (entity.getId() == null) {
                    setField(entity, "id", idGen.incrementAndGet());
                }
                if (entity.getCreatedAt() == null) {
                    setField(entity, "createdAt", Instant.now());
                }
                store.put(entity.getId(), entity);
                return entity;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override public Optional<Notification> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
        @Override public List<Notification> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Notification> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(Notification entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends Notification> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends Notification> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends Notification> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Notification> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<Notification> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public Notification getOne(Long aLong) { return store.get(aLong); }
        @Override public Notification getById(Long aLong) { return store.get(aLong); }
        @Override public Notification getReferenceById(Long aLong) { return store.get(aLong); }
        @Override public <S extends Notification> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends Notification> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends Notification> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends Notification> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends Notification> long count(Example<S> example) { return 0; }
        @Override public <S extends Notification> boolean exists(Example<S> example) { return false; }
        @Override public <S extends Notification, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<Notification> findAll(Sort sort) { return List.of(); }
        @Override public Page<Notification> findAll(Pageable pageable) { return Page.empty(); }
    }

    public static class FakeDeviceTokenRepository implements DeviceTokenRepository {
        private final Map<Long, DeviceToken> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(200);

        @Override
        public Optional<DeviceToken> findByToken(String token) {
            return store.values().stream().filter(t -> Objects.equals(t.getToken(), token)).findFirst();
        }

        @Override
        public Optional<DeviceToken> findByTokenAndUserId(String token, Long userId) {
            return store.values().stream()
                    .filter(t -> Objects.equals(t.getToken(), token) && Objects.equals(t.getUserId(), userId))
                    .findFirst();
        }

        @Override
        public List<DeviceToken> findByUserIdAndActiveTrue(Long userId) {
            return store.values().stream()
                    .filter(t -> Objects.equals(t.getUserId(), userId) && t.isActive())
                    .toList();
        }

        @Override
        public int deactivateAllByUserId(Long userId) {
            int count = 0;
            for (DeviceToken dt : store.values()) {
                if (Objects.equals(dt.getUserId(), userId) && dt.isActive()) {
                    dt.deactivate();
                    count++;
                }
            }
            return count;
        }

        @Override
        public <S extends DeviceToken> S save(S entity) {
            try {
                if (entity.getId() == null) {
                    setField(entity, "id", idGen.incrementAndGet());
                }
                store.put(entity.getId(), entity);
                return entity;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override public Optional<DeviceToken> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
        @Override public List<DeviceToken> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<DeviceToken> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(DeviceToken entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends DeviceToken> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends DeviceToken> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends DeviceToken> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends DeviceToken> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<DeviceToken> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public DeviceToken getOne(Long aLong) { return store.get(aLong); }
        @Override public DeviceToken getById(Long aLong) { return store.get(aLong); }
        @Override public DeviceToken getReferenceById(Long aLong) { return store.get(aLong); }
        @Override public <S extends DeviceToken> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends DeviceToken> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends DeviceToken> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends DeviceToken> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends DeviceToken> long count(Example<S> example) { return 0; }
        @Override public <S extends DeviceToken> boolean exists(Example<S> example) { return false; }
        @Override public <S extends DeviceToken, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<DeviceToken> findAll(Sort sort) { return List.of(); }
        @Override public Page<DeviceToken> findAll(Pageable pageable) { return Page.empty(); }
    }

    public static class FakeNotificationDeliveryRepository implements NotificationDeliveryRepository {
        private final Map<Long, NotificationDelivery> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(300);

        @Override
        public List<NotificationDelivery> findByStatusAndScheduledAtBefore(NotificationDelivery.Status status, Instant scheduledAt) {
            return store.values().stream()
                    .filter(d -> d.getStatus() == status && d.getScheduledAt().isBefore(scheduledAt))
                    .toList();
        }

        @Override
        public List<NotificationDelivery> findByNotificationId(Long notificationId) {
            return store.values().stream()
                    .filter(d -> Objects.equals(d.getNotificationId(), notificationId))
                    .toList();
        }

        @Override
        public <S extends NotificationDelivery> S save(S entity) {
            try {
                if (entity.getId() == null) {
                    setField(entity, "id", idGen.incrementAndGet());
                }
                store.put(entity.getId(), entity);
                return entity;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override public Optional<NotificationDelivery> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
        @Override public List<NotificationDelivery> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<NotificationDelivery> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(NotificationDelivery entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends NotificationDelivery> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends NotificationDelivery> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends NotificationDelivery> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends NotificationDelivery> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<NotificationDelivery> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public NotificationDelivery getOne(Long aLong) { return store.get(aLong); }
        @Override public NotificationDelivery getById(Long aLong) { return store.get(aLong); }
        @Override public NotificationDelivery getReferenceById(Long aLong) { return store.get(aLong); }
        @Override public <S extends NotificationDelivery> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends NotificationDelivery> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends NotificationDelivery> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends NotificationDelivery> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends NotificationDelivery> long count(Example<S> example) { return 0; }
        @Override public <S extends NotificationDelivery> boolean exists(Example<S> example) { return false; }
        @Override public <S extends NotificationDelivery, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<NotificationDelivery> findAll(Sort sort) { return List.of(); }
        @Override public Page<NotificationDelivery> findAll(Pageable pageable) { return Page.empty(); }
    }

    private static String getNotificationIdempotencyKey(Notification n) {
        try {
            Field f = Notification.class.getDeclaredField("idempotencyKey");
            f.setAccessible(true);
            return (String) f.get(n);
        } catch (Exception e) {
            return null;
        }
    }

    private static void setField(Object obj, String fieldName, Object val) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, val);
    }
}

