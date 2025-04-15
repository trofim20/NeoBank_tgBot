package org.example.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "users")
@Setter
@Getter
public class UserEntity extends BaseEntity {
    @Column
    private String username;

    @Column(unique = true)
    private Long telegramId;
}
