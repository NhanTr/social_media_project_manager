package com.nhantr.chat_service.testutil;

import com.nhantr.chat_service.entity.Conversation;
import com.nhantr.chat_service.entity.Message;
import com.nhantr.chat_service.repository.ConversationRepository;
import com.nhantr.chat_service.repository.MessageRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class TestChatRepositories {

    public static class FakeConversationRepository implements ConversationRepository {
        private final Map<Long, Conversation> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public Optional<Conversation> findByUserId1AndUserId2(Long userId1, Long userId2) {
            return store.values().stream()
                    .filter(c -> c.getUserId1().equals(userId1) && c.getUserId2().equals(userId2))
                    .findFirst();
        }

        @Override
        public List<Conversation> findAllForUser(Long userId) {
            return store.values().stream()
                    .filter(c -> c.contains(userId))
                    .sorted(Comparator.comparing(
                            c -> c.getLastMessageAt() == null ? c.getCreatedAt() : c.getLastMessageAt(),
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .toList();
        }

        @Override
        public <S extends Conversation> S save(S entity) {
            if (entity.getId() == null) {
                try {
                    Field idField = Conversation.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(entity, idGen.getAndIncrement());

                    Field createdField = Conversation.class.getDeclaredField("createdAt");
                    createdField.setAccessible(true);
                    if (createdField.get(entity) == null) {
                        createdField.set(entity, Instant.now());
                    }
                } catch (Exception ignored) {}
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public Optional<Conversation> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
        @Override public List<Conversation> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Conversation> findAllById(Iterable<Long> ids) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(Conversation entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Conversation> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends Conversation> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends Conversation> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Conversation> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<Conversation> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Conversation getOne(Long id) { return store.get(id); }
        @Override public Conversation getById(Long id) { return store.get(id); }
        @Override public Conversation getReferenceById(Long id) { return store.get(id); }
        @Override public <S extends Conversation> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends Conversation> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends Conversation> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends Conversation> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends Conversation> long count(Example<S> example) { return 0; }
        @Override public <S extends Conversation> boolean exists(Example<S> example) { return false; }
        @Override public <S extends Conversation, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<Conversation> findAll(Sort sort) { return List.of(); }
        @Override public Page<Conversation> findAll(Pageable pageable) { return Page.empty(); }
    }

    public static class FakeMessageRepository implements MessageRepository {
        private final Map<Long, Message> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(Long conversationId, Long senderId, String clientMessageId) {
            if (clientMessageId == null) return Optional.empty();
            return store.values().stream()
                    .filter(m -> m.getConversationId().equals(conversationId)
                            && m.getSenderId().equals(senderId)
                            && clientMessageId.equals(m.getClientMessageId()))
                    .findFirst();
        }

        @Override
        public List<Message> findByConversationIdOrderBySentAtDescIdDesc(Long conversationId, Pageable pageable) {
            return store.values().stream()
                    .filter(m -> m.getConversationId().equals(conversationId))
                    .sorted(Comparator.comparing(Message::getSentAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Comparator.comparing(Message::getId).reversed()))
                    .limit(pageable.getPageSize())
                    .toList();
        }

        @Override
        public List<Message> pageAfter(Long conversationId, Instant cursorTime, Long cursorId, Pageable pageable) {
            return store.values().stream()
                    .filter(m -> m.getConversationId().equals(conversationId))
                    .filter(m -> (m.getSentAt() != null && cursorTime != null && (m.getSentAt().isBefore(cursorTime) || (m.getSentAt().equals(cursorTime) && m.getId() < cursorId))))
                    .sorted(Comparator.comparing(Message::getSentAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Comparator.comparing(Message::getId).reversed()))
                    .limit(pageable.getPageSize())
                    .toList();
        }

        @Override
        public int markDelivered(Long conversationId, Long receiverId, Message.Status deliveredStatus, Instant now) {
            int count = 0;
            for (Message m : store.values()) {
                if (m.getConversationId().equals(conversationId) && !m.getSenderId().equals(receiverId)) {
                    if (m.markDelivered(now)) {
                        count++;
                    }
                }
            }
            return count;
        }

        @Override
        public int markRead(Long conversationId, Long readerId, Message.Status readStatus, Instant now) {
            int count = 0;
            for (Message m : store.values()) {
                if (m.getConversationId().equals(conversationId) && !m.getSenderId().equals(readerId)) {
                    if (m.markRead(now)) {
                        count++;
                    }
                }
            }
            return count;
        }

        @Override
        public <S extends Message> S save(S entity) {
            if (entity.getId() == null) {
                try {
                    Field idField = Message.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(entity, idGen.getAndIncrement());

                    Field sentField = Message.class.getDeclaredField("sentAt");
                    sentField.setAccessible(true);
                    if (sentField.get(entity) == null) {
                        sentField.set(entity, Instant.now());
                    }
                } catch (Exception ignored) {}
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public Optional<Message> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
        @Override public List<Message> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Message> findAllById(Iterable<Long> ids) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(Message entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Message> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends Message> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends Message> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Message> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<Message> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Message getOne(Long id) { return store.get(id); }
        @Override public Message getById(Long id) { return store.get(id); }
        @Override public Message getReferenceById(Long id) { return store.get(id); }
        @Override public <S extends Message> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends Message> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends Message> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends Message> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends Message> long count(Example<S> example) { return 0; }
        @Override public <S extends Message> boolean exists(Example<S> example) { return false; }
        @Override public <S extends Message, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<Message> findAll(Sort sort) { return List.of(); }
        @Override public Page<Message> findAll(Pageable pageable) { return Page.empty(); }
    }
}

