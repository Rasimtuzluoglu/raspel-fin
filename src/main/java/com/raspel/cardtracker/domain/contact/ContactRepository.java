package com.raspel.cardtracker.domain.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByName(String name);
    List<Contact> findByNameContainingIgnoreCase(String name);
}
