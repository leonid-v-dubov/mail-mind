package de.leonidu.mailmind.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LastProcessedUidRepository extends JpaRepository<LastProcessedUidEntity, String> {
}