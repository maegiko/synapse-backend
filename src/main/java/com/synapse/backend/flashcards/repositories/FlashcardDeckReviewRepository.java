package com.synapse.backend.flashcards.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.flashcards.entities.FlashcardDeckReview;

public interface FlashcardDeckReviewRepository extends JpaRepository<FlashcardDeckReview, Long> {

}
