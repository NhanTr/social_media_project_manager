package com.nhantr.ai_service.testutil;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
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

public final class TestAiRepositories {

    public static class FakeAiGenerationRepository implements AiGenerationRepository {
        private final Map<Long, AiGeneration> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public Optional<AiGeneration> findByIdAndUserId(Long id, Long userId) {
            AiGeneration gen = store.get(id);
            return (gen != null && Objects.equals(gen.getUserId(), userId)) ? Optional.of(gen) : Optional.empty();
        }

        @Override
        public Optional<AiGeneration> findByUserIdAndClientRequestId(Long userId, String clientRequestId) {
            if (clientRequestId == null) return Optional.empty();
            return store.values().stream()
                    .filter(g -> Objects.equals(g.getUserId(), userId)
                            && clientRequestId.equals(g.getClientRequestId()))
                    .findFirst();
        }

        @Override
        public List<AiGeneration> findClaimableJobs(Instant now, Pageable pageable) {
            return store.values().stream()
                    .filter(g -> (g.getStatus() == AiGeneration.Status.PENDING
                            && (g.getLeasedUntil() == null || g.getLeasedUntil().isBefore(now)))
                            || (g.getStatus() == AiGeneration.Status.PROCESSING
                            && g.getLeasedUntil() != null && g.getLeasedUntil().isBefore(now)))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .limit(pageable.getPageSize())
                    .toList();
        }

        @Override
        public synchronized int atomicRetry(Long id, Long userId, Instant now) {
            AiGeneration g = store.get(id);
            if (g != null && Objects.equals(g.getUserId(), userId) && g.getStatus() == AiGeneration.Status.FAILED) {
                g.retry();
                return 1;
            }
            return 0;
        }

        @Override
        public <S extends AiGeneration> S save(S entity) {
            if (entity.getId() == null) {
                assignId(entity, idGen.getAndIncrement());
                assignCreatedAt(entity, Instant.now());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public <S extends AiGeneration> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public Optional<AiGeneration> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public List<AiGeneration> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<AiGeneration> findAllById(Iterable<Long> ids) {
            List<AiGeneration> result = new ArrayList<>();
            for (Long id : ids) {
                if (store.containsKey(id)) result.add(store.get(id));
            }
            return result;
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
        public void delete(AiGeneration entity) {
            if (entity.getId() != null) store.remove(entity.getId());
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            for (Long id : ids) store.remove(id);
        }

        @Override
        public void deleteAll(Iterable<? extends AiGeneration> entities) {
            for (AiGeneration e : entities) delete(e);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void flush() {}

        @Override
        public <S extends AiGeneration> List<S> saveAllAndFlush(Iterable<S> entities) {
            List<S> res = new ArrayList<>();
            for (S e : entities) res.add(save(e));
            return res;
        }

        @Override
        public void deleteAllInBatch(Iterable<AiGeneration> entities) {
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
        public AiGeneration getOne(Long id) {
            return store.get(id);
        }

        @Override
        public AiGeneration getById(Long id) {
            return store.get(id);
        }

        @Override
        public AiGeneration getReferenceById(Long id) {
            return store.get(id);
        }

        @Override
        public <S extends AiGeneration> List<S> findAll(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration> List<S> findAll(Example<S> example, Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration> List<S> saveAll(Iterable<S> entities) {
            List<S> res = new ArrayList<>();
            for (S e : entities) res.add(save(e));
            return res;
        }

        @Override
        public List<AiGeneration> findAll(Sort sort) {
            return findAll();
        }

        @Override
        public Page<AiGeneration> findAll(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration> Optional<S> findOne(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration> long count(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration> boolean exists(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends AiGeneration, R> R findBy(
                Example<S> example,
                Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction
        ) {
            throw new UnsupportedOperationException();
        }

        private static void assignId(AiGeneration entity, Long id) {
            try {
                Field f = AiGeneration.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static void assignCreatedAt(AiGeneration entity, Instant time) {
            try {
                Field f = AiGeneration.class.getDeclaredField("createdAt");
                f.setAccessible(true);
                f.set(entity, time);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
