package com.raspel.cardtracker.domain.cheque;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.raspel.cardtracker.domain.contact.Contact;

@Entity
@Table(name = "cheque")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cheque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    private Integer version;

    @Column(name = "cheque_number", nullable = false, length = 100)
    @NotBlank
    private String chequeNumber;

    @Column(nullable = false, length = 100)
    @NotBlank
    private String bank;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(nullable = false, precision = 15, scale = 2)
    @Positive
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    @NotBlank
    private String party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChequeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChequeStatus status;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
