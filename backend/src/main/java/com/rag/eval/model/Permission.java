package com.rag.eval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_permission")
public class Permission {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "perm_group", nullable = false)
    private String group;

    public Permission(String code, String name, String group) {
        this.code = code;
        this.name = name;
        this.group = group;
    }
}
