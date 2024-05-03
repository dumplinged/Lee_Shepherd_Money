package com.shepherdmoney.interviewproject.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@ToString(exclude = {"cards"})
// I excluded cards to prevent potential endless recursion of toString calls
@RequiredArgsConstructor
@Table(name = "MyUser")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private String name;

    private String email;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CreditCard> cards = new ArrayList<>();

    // Kathy: I chose a list of CreditCards because it is an easy way to represent and organize all possible
    // numbers of credit cards that a user can have (0, 1, or many).
    // "OneToMany" -> a User can have multiple instances of CreditCard.
    // mappedBy -> specifies that a CreditCard instance has a field named user that references the owning User instance.
    // cascade -> any operations applied to User are applied on the CreditCards it owns.
    // orphanRemoval -> when you remove a CreditCard from a User's list of cards and that CreditCard is not associated
    // with any other User (it shouldn't be), it will be deleted from the database automatically.
}
