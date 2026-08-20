package com.rag.eval.repository;

import com.rag.eval.model.EvaluationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationQuestionRepo extends JpaRepository<EvaluationQuestion, String> {
    List<EvaluationQuestion> findAllByOrderByIdAsc();
}
