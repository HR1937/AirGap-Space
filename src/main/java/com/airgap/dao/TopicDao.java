package com.airgap.dao;

import com.airgap.config.HibernateUtil;
import com.airgap.model.Topic;
import com.airgap.model.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class TopicDao {

    static {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.createNativeQuery("ALTER TABLE topics MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'CAPTURED'", Object.class).executeUpdate();
            tx.commit();
        } catch (Exception e) {
            System.err.println("[TopicDao Schema Sync Warning]: " + e.getMessage());
        }
    }

    public Topic save(Topic topic) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            if (topic.getUser() != null && topic.getUser().getId() != null) {
                User managedUser = session.get(User.class, topic.getUser().getId());
                if (managedUser != null) {
                    topic.setUser(managedUser);
                }
            }
            session.persist(topic);
            transaction.commit();
            return topic;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rbEx) {
                    System.err.println("[TopicDao.save Rollback Error]: " + rbEx.getMessage());
                }
            }
            System.err.println("[TopicDao.save ERROR]: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error saving topic: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public List<Topic> findByUserId(Long userId) {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            Query<Topic> query = session.createQuery(
                    "FROM Topic t JOIN FETCH t.user WHERE t.user.id = :userId ORDER BY t.isPinned DESC, t.createdAt DESC", Topic.class);
            query.setParameter("userId", userId);
            List<Topic> list = query.getResultList();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[TopicDao.findByUserId ERROR]: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public List<String> findRecentTopicTitlesByUserId(Long userId, int limit) {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            Query<String> query = session.createQuery(
                    "SELECT t.title FROM Topic t WHERE t.user.id = :userId ORDER BY t.createdAt DESC", String.class);
            query.setParameter("userId", userId);
            query.setMaxResults(limit);
            List<String> list = query.getResultList();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[TopicDao.findRecentTopicTitlesByUserId ERROR]: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public List<Topic> findQueuedTopics(Long userId) {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            Query<Topic> query = session.createQuery(
                    "FROM Topic t JOIN FETCH t.user WHERE t.user.id = :userId AND (t.status = :s1 OR t.status = :s2 OR t.status = :s3 OR t.status = :s4) ORDER BY t.createdAt ASC", Topic.class);
            query.setParameter("userId", userId);
            query.setParameter("s1", Topic.Status.CAPTURED);
            query.setParameter("s2", Topic.Status.WAITING_FOR_NETWORK);
            query.setParameter("s3", Topic.Status.FAILED);
            query.setParameter("s4", Topic.Status.AI_UNAVAILABLE);
            List<Topic> list = query.getResultList();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[TopicDao.findQueuedTopics ERROR]: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public Topic findById(Long id) {
        Transaction tx = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();
            Query<Topic> query = session.createQuery(
                    "FROM Topic t JOIN FETCH t.user WHERE t.id = :id", Topic.class);
            query.setParameter("id", id);
            Topic topic = query.uniqueResult();
            if (topic != null) {
                topic.setLastOpenedAt(new Date());
                topic.setTimesRead(topic.getTimesRead() + 1);
                session.merge(topic);
            }
            tx.commit();
            return topic;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rbEx) {
                    System.err.println("[TopicDao.findById Rollback Error]: " + rbEx.getMessage());
                }
            }
            System.err.println("[TopicDao.findById ERROR]: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void updateStatus(Long id, Topic.Status status) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            Topic topic = session.get(Topic.class, id);
            if (topic != null) {
                topic.setStatus(status);
                session.merge(topic);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rbEx) {}
            }
            System.err.println("[TopicDao.updateStatus ERROR]: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void updateEnrichedContent(Long id, String summary, String knowledgePackJson, 
                                     String teachingPlanJson, String curiosityPathsJson, 
                                     String relatedConceptsJson, int readingTime) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            Topic topic = session.get(Topic.class, id);
            if (topic != null) {
                topic.setSummaryContent(summary);
                topic.setKnowledgePackJson(knowledgePackJson);
                topic.setTeachingPlanJson(teachingPlanJson);
                topic.setCuriosityPathsJson(curiosityPathsJson);
                topic.setRelatedConceptsJson(relatedConceptsJson);
                topic.setEstimatedReadingTime(readingTime > 0 ? readingTime : 1);
                topic.setStatus(Topic.Status.READY_OFFLINE);
                session.merge(topic);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rbEx) {}
            }
            System.err.println("[TopicDao.updateEnrichedContent ERROR]: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void incrementQuestionsAsked(Long id) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            Topic topic = session.get(Topic.class, id);
            if (topic != null) {
                topic.setQuestionsAsked(topic.getQuestionsAsked() + 1);
                session.merge(topic);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try { transaction.rollback(); } catch (Exception rbEx) {}
            }
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public int delete(Long id, Long userId) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            Topic existingTopic = session.get(Topic.class, id);
            if (existingTopic != null && !existingTopic.getUser().getId().equals(userId)) {
                throw new SecurityException("Topic does not belong to the current user.");
            }

            int affectedRows = existingTopic == null ? 0 : session.createMutationQuery(
                    "delete from Topic t where t.id = :id and t.user.id = :userId")
                    .setParameter("id", id)
                    .setParameter("userId", userId)
                    .executeUpdate();

            session.flush();
            Long remainingRows = session.createQuery(
                    "select count(t) from Topic t where t.id = :id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult();
            if (remainingRows != 0) {
                throw new IllegalStateException("Delete verification failed: topic row still exists.");
            }

            transaction.commit();
            System.out.println("[DELETE] DAO delete success");
            System.out.println("[DELETE] MySQL affected rows: " + affectedRows);
            System.out.println("[DELETE] SELECT verification remaining rows: " + remainingRows);
            return affectedRows;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rbEx) {}
            }
            System.err.println("[TopicDao.delete ERROR]: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error deleting topic: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public boolean togglePin(Long id, Long userId) {
        return togglePin(id, userId, null);
    }

    public boolean togglePin(Long id, Long userId, Boolean requestedState) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            Topic topic = session.get(Topic.class, id);
            if (topic == null || !topic.getUser().getId().equals(userId)) {
                throw new SecurityException("Topic does not belong to the current user.");
            }

            boolean newPinState = requestedState != null ? requestedState : !topic.isPinned();
            int affectedRows = session.createMutationQuery(
                    "update Topic t set t.isPinned = :isPinned where t.id = :id and t.user.id = :userId")
                    .setParameter("isPinned", newPinState)
                    .setParameter("id", id)
                    .setParameter("userId", userId)
                    .executeUpdate();

            session.flush();
            session.clear();
            Topic verifiedTopic = session.get(Topic.class, id);
            if (verifiedTopic == null || verifiedTopic.isPinned() != newPinState) {
                throw new IllegalStateException("Pin verification failed: MySQL is_pinned did not persist.");
            }

            transaction.commit();
            System.out.println("[PIN] DAO success");
            System.out.println("[PIN] MySQL is_pinned updated: " + newPinState);
            System.out.println("[PIN] MySQL affected rows: " + affectedRows);
            return newPinState;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rbEx) {}
            }
            System.err.println("[TopicDao.togglePin ERROR]: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error toggling pin: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
}
