package com.app.repository;

import com.app.model.CustomerKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerKeyRepository extends JpaRepository<CustomerKey, String> {

  Optional<CustomerKey> findByCustomerIdHmac(String customerIdHmac);
}
