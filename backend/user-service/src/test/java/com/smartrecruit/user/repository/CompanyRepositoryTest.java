package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.Company;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BE-COMP-01 — membership lookup and the one-company-per-recruiter guarantee. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CompanyRepositoryTest {

    @Autowired
    private CompanyRepository repository;

    private Company company(UUID... members) {
        return Company.builder().id(UUID.randomUUID()).name("Acme").sector("IT")
                .members(new ArrayList<>(List.of(members))).build();
    }

    @Test
    void findsTheCompanyOfAMember() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Company acme = repository.saveAndFlush(company(alice, bob));
        repository.saveAndFlush(company(UUID.randomUUID()));

        assertThat(repository.findByMember(bob)).get().extracting(Company::getId).isEqualTo(acme.getId());
        assertThat(repository.findByMember(UUID.randomUUID())).isEmpty();
    }

    @Test
    void databaseRefusesAMemberInTwoCompanies() {
        UUID alice = UUID.randomUUID();
        repository.saveAndFlush(company(alice));

        assertThatThrownBy(() -> repository.saveAndFlush(company(alice)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
