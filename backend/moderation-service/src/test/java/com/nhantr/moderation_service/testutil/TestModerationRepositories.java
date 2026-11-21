package com.nhantr.moderation_service.testutil;

import com.nhantr.moderation_service.entity.ModerationAuditLog;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.repository.ModerationAuditLogRepository;
import com.nhantr.moderation_service.repository.ReportRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class TestModerationRepositories {

    public static class FakeReportRepository implements ReportRepository {
        private final Map<Long, Report> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public Optional<Report> findByReporterIdAndReportedPostIdAndStatus(
                Long reporterId,
                Long reportedPostId,
                Report.Status status
        ) {
            return store.values().stream()
                    .filter(r -> Objects.equals(r.getReporterId(), reporterId)
                            && Objects.equals(r.getReportedPostId(), reportedPostId)
                            && r.getStatus() == status)
                    .findFirst();
        }

        @Override
        public Optional<Report> findByReporterIdAndReportedUserIdAndStatus(
                Long reporterId,
                Long reportedUserId,
                Report.Status status
        ) {
            return store.values().stream()
                    .filter(r -> Objects.equals(r.getReporterId(), reporterId)
                            && Objects.equals(r.getReportedUserId(), reportedUserId)
                            && r.getStatus() == status)
                    .findFirst();
        }

        @Override
        public List<Report> findByReportedPostIdAndStatus(Long reportedPostId, Report.Status status) {
            return store.values().stream()
                    .filter(r -> Objects.equals(r.getReportedPostId(), reportedPostId)
                            && r.getStatus() == status)
                    .toList();
        }

        @Override
        public List<Report> findByReportedUserIdAndStatus(Long reportedUserId, Report.Status status) {
            return store.values().stream()
                    .filter(r -> Objects.equals(r.getReportedUserId(), reportedUserId)
                            && r.getStatus() == status)
                    .toList();
        }

        @Override
        public long countByReportedPostIdAndStatus(Long reportedPostId, Report.Status status) {
            return store.values().stream()
                    .filter(r -> Objects.equals(r.getReportedPostId(), reportedPostId)
                            && r.getStatus() == status)
                    .count();
        }

        @Override
        public long countByReportedUserIdAndStatus(Long reportedUserId, Report.Status status) {
            return store.values().stream()
                    .filter(r -> Objects.equals(r.getReportedUserId(), reportedUserId)
                            && r.getStatus() == status)
                    .count();
        }

        @Override
        public synchronized int atomicResolve(
                Long id,
                Report.Status newStatus,
                Report.Action action,
                Long adminId,
                String note,
                Instant now
        ) {
            Report r = store.get(id);
            if (r != null && r.getStatus() == Report.Status.PENDING) {
                r.resolve(adminId, action, note);
                return 1;
            }
            return 0;
        }

        @Override
        public List<Report> findAllByOrderByCreatedAtDesc() {
            return store.values().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        @Override
        public Page<Report> findByStatus(Report.Status status, Pageable pageable) {
            List<Report> filtered = store.values().stream()
                    .filter(r -> r.getStatus() == status)
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<Report> sub = start <= end ? filtered.subList(start, end) : List.of();
            return new PageImpl<>(sub, pageable, filtered.size());
        }

        @Override
        public Page<Report> findAll(Pageable pageable) {
            List<Report> all = findAllByOrderByCreatedAtDesc();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), all.size());
            List<Report> sub = start <= end ? all.subList(start, end) : List.of();
            return new PageImpl<>(sub, pageable, all.size());
        }

        @Override
        public <S extends Report> S save(S entity) {
            if (entity.getId() == null) {
                assignId(entity, idGen.getAndIncrement());
                assignCreatedAt(entity, Instant.now());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<Report> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public List<Report> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Report> findAllById(Iterable<Long> ids) {
            List<Report> res = new ArrayList<>();
            for (Long id : ids) if (store.containsKey(id)) res.add(store.get(id));
            return res;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        @Override
        public void delete(Report entity) {
            if (entity.getId() != null) store.remove(entity.getId());
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            for (Long id : ids) store.remove(id);
        }

        @Override
        public void deleteAll(Iterable<? extends Report> entities) {
            for (Report r : entities) delete(r);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void flush() {}

        @Override
        public <S extends Report> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public <S extends Report> List<S> saveAllAndFlush(Iterable<S> entities) {
            List<S> res = new ArrayList<>();
            for (S e : entities) res.add(save(e));
            return res;
        }

        @Override
        public void deleteAllInBatch(Iterable<Report> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        public Report getOne(Long id) {
            return store.get(id);
        }

        @Override
        public Report getById(Long id) {
            return store.get(id);
        }

        @Override
        public Report getReferenceById(Long id) {
            return store.get(id);
        }

        @Override
        public <S extends Report> List<S> findAll(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Report> List<S> findAll(Example<S> example, Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Report> List<S> saveAll(Iterable<S> entities) {
            List<S> res = new ArrayList<>();
            for (S e : entities) res.add(save(e));
            return res;
        }

        @Override
        public List<Report> findAll(Sort sort) {
            return findAll();
        }

        @Override
        public <S extends Report> Optional<S> findOne(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Report> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Report> long count(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Report> boolean exists(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Report, R> R findBy(
                Example<S> example,
                Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction
        ) {
            throw new UnsupportedOperationException();
        }

        private static void assignId(Report entity, Long id) {
            try {
                Field f = Report.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static void assignCreatedAt(Report entity, Instant time) {
            try {
                Field f = Report.class.getDeclaredField("createdAt");
                f.setAccessible(true);
                f.set(entity, time);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class FakeModerationAuditLogRepository implements ModerationAuditLogRepository {
        private final Map<Long, ModerationAuditLog> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public List<ModerationAuditLog> findByReportIdOrderByCreatedAtDesc(Long reportId) {
            return store.values().stream()
                    .filter(a -> Objects.equals(a.getReportId(), reportId))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        @Override
        public List<ModerationAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId) {
            return store.values().stream()
                    .filter(a -> Objects.equals(a.getTargetType(), targetType)
                            && Objects.equals(a.getTargetId(), targetId))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        @Override
        public List<ModerationAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId) {
            return store.values().stream()
                    .filter(a -> Objects.equals(a.getAdminId(), adminId))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        @Override
        public List<ModerationAuditLog> findAllByOrderByCreatedAtDesc() {
            return store.values().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        @Override
        public <S extends ModerationAuditLog> S save(S entity) {
            if (entity.getId() == null) {
                assignId(entity, idGen.getAndIncrement());
                assignCreatedAt(entity, Instant.now());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<ModerationAuditLog> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public List<ModerationAuditLog> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<ModerationAuditLog> findAllById(Iterable<Long> ids) {
            List<ModerationAuditLog> res = new ArrayList<>();
            for (Long id : ids) if (store.containsKey(id)) res.add(store.get(id));
            return res;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        @Override
        public void delete(ModerationAuditLog entity) {
            if (entity.getId() != null) store.remove(entity.getId());
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            for (Long id : ids) store.remove(id);
        }

        @Override
        public void deleteAll(Iterable<? extends ModerationAuditLog> entities) {
            for (ModerationAuditLog a : entities) delete(a);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void flush() {}

        @Override
        public <S extends ModerationAuditLog> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public <S extends ModerationAuditLog> List<S> saveAllAndFlush(Iterable<S> entities) {
            List<S> res = new ArrayList<>();
            for (S e : entities) res.add(save(e));
            return res;
        }

        @Override
        public void deleteAllInBatch(Iterable<ModerationAuditLog> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        public ModerationAuditLog getOne(Long id) {
            return store.get(id);
        }

        @Override
        public ModerationAuditLog getById(Long id) {
            return store.get(id);
        }

        @Override
        public ModerationAuditLog getReferenceById(Long id) {
            return store.get(id);
        }

        @Override
        public <S extends ModerationAuditLog> List<S> findAll(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog> List<S> findAll(Example<S> example, Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog> List<S> saveAll(Iterable<S> entities) {
            List<S> res = new ArrayList<>();
            for (S e : entities) res.add(save(e));
            return res;
        }

        @Override
        public List<ModerationAuditLog> findAll(Sort sort) {
            return findAll();
        }

        @Override
        public Page<ModerationAuditLog> findAll(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog> Optional<S> findOne(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog> long count(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog> boolean exists(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends ModerationAuditLog, R> R findBy(
                Example<S> example,
                Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction
        ) {
            throw new UnsupportedOperationException();
        }

        private static void assignId(ModerationAuditLog entity, Long id) {
            try {
                Field f = ModerationAuditLog.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static void assignCreatedAt(ModerationAuditLog entity, Instant time) {
            try {
                Field f = ModerationAuditLog.class.getDeclaredField("createdAt");
                f.setAccessible(true);
                f.set(entity, time);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
