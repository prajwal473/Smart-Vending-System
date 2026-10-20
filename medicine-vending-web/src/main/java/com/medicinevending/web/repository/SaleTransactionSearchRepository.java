package com.medicinevending.web.repository;

import com.medicinevending.web.model.SaleTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Read-only, filtered access to the existing "transactions" table for the admin history page.
 * Built with the JPA Criteria API, so every user-supplied value is sent to the database as a bound
 * parameter - no SQL/JPQL text is ever concatenated from input. List, count and sum all use the SAME
 * predicates, so the summary always matches the rows being paged through.
 */
@Repository
public class SaleTransactionSearchRepository {

	/** idMatch / namePattern / from / toExclusive are each optional (null = not filtering on that). */
	public record Criteria(Integer idMatch, String namePattern, LocalDateTime from, LocalDateTime toExclusive) {
	}

	/** Escape character used in LIKE patterns (see TransactionHistoryService.likePattern). */
	public static final char LIKE_ESCAPE = '!';

	@PersistenceContext
	private EntityManager em;

	/** One page of matches, newest first (transaction date, then id as a tie-breaker). */
	public List<SaleTransaction> find(Criteria criteria, int offset, int limit) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<SaleTransaction> query = cb.createQuery(SaleTransaction.class);
		Root<SaleTransaction> root = query.from(SaleTransaction.class);
		query.select(root)
				.where(predicates(cb, root, criteria).toArray(new Predicate[0]))
				.orderBy(cb.desc(root.get("transactionDate")), cb.desc(root.get("transactionId")));
		return em.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
	}

	public long count(Criteria criteria) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<SaleTransaction> root = query.from(SaleTransaction.class);
		query.select(cb.count(root)).where(predicates(cb, root, criteria).toArray(new Predicate[0]));
		Long result = em.createQuery(query).getSingleResult();
		return result == null ? 0L : result;
	}

	/** Sum of total_amount over the matches; zero when nothing matches (SQL SUM would be NULL). */
	public BigDecimal sumTotalAmount(Criteria criteria) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
		Root<SaleTransaction> root = query.from(SaleTransaction.class);
		query.select(cb.sum(root.<BigDecimal>get("totalAmount")))
				.where(predicates(cb, root, criteria).toArray(new Predicate[0]));
		BigDecimal result = em.createQuery(query).getSingleResult();
		return result == null ? BigDecimal.ZERO : result;
	}

	private static List<Predicate> predicates(CriteriaBuilder cb, Root<SaleTransaction> root, Criteria c) {
		List<Predicate> all = new ArrayList<>();

		if (c.idMatch() != null || c.namePattern() != null) {
			List<Predicate> either = new ArrayList<>();
			if (c.idMatch() != null) {
				either.add(cb.equal(root.get("transactionId"), c.idMatch()));
			}
			if (c.namePattern() != null) {
				either.add(cb.like(cb.lower(root.<String>get("medicineName")), c.namePattern(), LIKE_ESCAPE));
			}
			all.add(cb.or(either.toArray(new Predicate[0])));
		}
		if (c.from() != null) {
			all.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("transactionDate"), c.from()));
		}
		if (c.toExclusive() != null) {
			all.add(cb.lessThan(root.<LocalDateTime>get("transactionDate"), c.toExclusive()));
		}
		return all;
	}
}
