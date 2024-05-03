package com.shepherdmoney.interviewproject.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private String issuanceBank;

    private String number;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    // Kathy: The "ManyToOne" tag implies that many CreditCards can be associated with one User.
    // JoinColumn -> "user_id" will be the name of the foreign key column in CreditCard's table that corresponds
    // to the User entity, as the representative column of the CreditCard's user will contain the user ids.

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "creditCard") // One CreditCard associated with 1+ BalanceHistorys
    private List<BalanceHistory> balanceHistory = new LinkedList<>();
    // Kathy: The idea is that BalanceHistory items will be inserted into the private data member balanceHistory
    // in ascending order based on date, so that the first element of the LinkedList is the "largest" or most
    // recent date.
    // The interface I'm using is list, to work smoothly with Spring Boot Hibernate, but my implementation is
    // in LinkedList. I will cast it accordingly if needed. For example, if my code is working with a lot of traversals
    // I wouldn't cast it to LinkedList to protect performance time.

    // In choosing LinkedList vs TreeMap:
    // Retrieval: O(n) vs O(log n)
    // Traversal: O(n) vs O(n * log n)
    // Insertion: O(1) / O(n) vs O(log n)
    // Deletion: O(1) / O(n) vs O(log n)
    // I thought that banks would most commonly be inserting and deleting information from the front and back, which
    // is faster on LinkedLists.

    private static final Logger LOGGER = Logger.getLogger(CreditCard.class.getName());
    // Retrieval of a balance of a single day
    public BalanceHistory getBalanceForDate(LocalDate date) {
        int index = searchByDate(date);
        // The searchByDate will find the index of the BalanceHistory with the exact date value, if it
        // exists. searchByDate also checks the case that the balanceHistory list is empty and returns -1 if so.
        // Otherwise, I call the function getClosestPreviousBalance, which finds the closest previous date, if
        // it exists.
        if (index >= 0) {
            return balanceHistory.get(index);
        } else {
            return getClosestPreviousBalance(date);
        }
    }

    // Deletion of a balance
    public void removeBalance(BalanceHistory balance) {
        // We look to see if that balance exists in this CreditCards's balanceHistory.
        int index = searchByDate(balance.getDate());
        if (index >= 0 && index < balanceHistory.size()) {
            balanceHistory.remove(index);
        } else {
            // Log an error if the index to delete is out of bounds, aka that the requested balance is not associated
            // inside the balanceHistory of this CreditCard.
            LOGGER.log(Level.SEVERE, "Index is out of bounds. Unable to remove element.");
            LOGGER.log(Level.SEVERE, "Index: " + index);
        }
    }

    // Insertion of a new balance
    public void addBalance(BalanceHistory balance) {
        // Convert balanceHistory to a LinkedList for traversal
        LinkedList<BalanceHistory> balanceHistory_link = new LinkedList<>(balanceHistory);

        // Traverse the linked list to find the insertion point
        ListIterator<BalanceHistory> iterator = balanceHistory_link.listIterator();
        while (iterator.hasNext()) {
            BalanceHistory current = iterator.next();
            // Compare the date of the current balance with the date of the new balance
            if (balance.getDate().isAfter(current.getDate())) {
                // Insert the new balance before the current balance
                iterator.previous(); // Move the iterator back to the current position
                iterator.add(balance); // Insert the new balance
                // Finally, convert the updated LinkedList back to the balanceHistory list
                balanceHistory = new ArrayList<>(balanceHistory_link);
                return; // Exit the method after insertion
            }
        }

        // This portion of the code will only be accessed if the new balance date is the smallest, so insert it
        // at the end. The balanceHistory list is organized from most "big" or recent date to more "small" or
        // least recent.
        balanceHistory_link.addLast(balance);
        // Convert the updated LinkedList back to the balanceHistory list
        balanceHistory = new ArrayList<>(balanceHistory_link);
    }

    // Helper method to retrieve the closest previous balance date.
    private BalanceHistory getClosestPreviousBalance(LocalDate date) {
        int index = searchByDate(date);
        if (index >= 0) {
            return balanceHistory.get(index); // If the date exists, return the balance for that date
        } else {
            int insertionPoint = -index - 1;
            if (insertionPoint == 0) {
                LOGGER.log(Level.SEVERE, "No previous balance found.");
                return null; // No previous balance, so we log the issue and return null.
            } else {
                return balanceHistory.get(insertionPoint - 1); // Return the closest previous balance
            }
        }
    }

    // Helper Binary search for finding the index of the balance entry with the given date
    // If the list balanceHistory is empty, -1 is sent back to show there is no index with that date.
    // If not empty, the binary search function is sent to retrieve the actual index.
    private int searchByDate(LocalDate date) {
        return balanceHistory.isEmpty() ? -1 : binarySearchRecursive(date, 0, balanceHistory.size() - 1);
    }

    private int binarySearchRecursive(LocalDate date, int start_index, int end_index) {
        if (start_index > end_index) {
            return start_index-1; // Returning next closest index
        }
        // We get the middle or close to middle entry in balanceHistory to begin a recursive search.
        int mid = start_index + (end_index - start_index) / 2;
        LocalDate midDate = balanceHistory.get(mid).getDate();
        int difference = date.compareTo(midDate);
        // This is a pre-order binary search, meaning it checks the current node before searching recursively through
        // the left section or right section, based on which side the goal date is on. This allows for O(log n)
        // traversal.
        if (difference == 0) {
            return mid; // Found!
        } else if (difference < 0) {
            return binarySearchRecursive(date, start_index, mid - 1); // Search left
        } else {
            return binarySearchRecursive(date, mid + 1, end_index); // Search right
        }
    }

}