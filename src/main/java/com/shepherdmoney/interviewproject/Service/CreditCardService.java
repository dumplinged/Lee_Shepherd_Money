package com.shepherdmoney.interviewproject.Service;
import com.shepherdmoney.interviewproject.model.BalanceHistory;
import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.shepherdmoney.interviewproject.repository.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;

@Service
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;

    @Autowired
    public CreditCardService(CreditCardRepository creditCardRepository) {
        this.creditCardRepository = creditCardRepository;
    }

    public void updateBalanceHistory(UpdateBalancePayload[] payload) {
        // Iterate through all the transactions in the array of UpdateBalancePayload objects.
        for (UpdateBalancePayload transaction : payload) {
            String creditCardNumber = transaction.getCreditCardNumber();

            // creditCard shouldn't be null because we already checked for that in CreditCard controller.
            CreditCard creditCard = creditCardRepository.findByNumber(creditCardNumber).orElse(null);

            List<BalanceHistory> balanceHistory = creditCard.getBalanceHistory();
            double transactionAmount = transaction.getBalanceAmount();
            LocalDate transactionDate = transaction.getBalanceDate();

            // If the balanceHistory associated to this creditCard is null -> make a new list.
            if (balanceHistory == null) {
                balanceHistory = new LinkedList<>();
            }

            // // If the balanceHistory is empty ->
            // 1. Create the entry and propagate it using information from the UpdateBalancePayload data.
            // 2. Add an entry for today's date and then fill the gaps between the two using fillBalanceHistoryGaps.
            // 3. Sort the entire list and save the edited creditCard to the repository.
            // 4. Continue to move on to the next transaction in the UpdateBalancePayload array.
            if (balanceHistory.isEmpty()) {
                BalanceHistory newEntry = new BalanceHistory();
                newEntry.setDate(transactionDate);
                newEntry.setBalance(transactionAmount);
                newEntry.setCreditCard(creditCard); // step 1

                balanceHistory.add(newEntry);
                addEntryForToday(balanceHistory, creditCard);
                fillBalanceHistoryGaps(balanceHistory, creditCard, creditCardRepository); // step 2

                balanceHistory.sort((entry1, entry2) -> entry2.getDate().compareTo(entry1.getDate()));
                creditCardRepository.save(creditCard); // step 3
                continue; // step 4
            }

            // Now we know that the creditCard has at least 1 BalanceHistory in its list.
            // Add a BalanceHistory for today's date with most updated balance. If this is not needed
            // there's an appropriate check inside addEntryForToday.
            addEntryForToday(balanceHistory, creditCard);

            // Fill in BalanceHistory of that card based on all current BalanceHistory objects inside it.
            fillBalanceHistoryGaps(balanceHistory, creditCard, creditCardRepository);

            // Now, we add the new transaction.
            // The boolean dateIncluded is whether the transaction is included within the range of balanceHistory.
            boolean dateIncluded = false;
            for (BalanceHistory entry : balanceHistory) {
                if (entry.getDate().isEqual(transactionDate)) {
                    // If the date of this transaction is included, set the boolean accordingly and insert the
                    // balance into the appropriate BalanceHistory entry in balanceHistory.
                    double difference = transactionAmount - entry.getBalance();
                    entry.setBalance(transactionAmount);
                    dateIncluded = true;
                    // If the difference in balance value is not 0, update all subsequence balances in balanceHistory.
                    if (difference != 0) {
                        updateSubsequentBalances(balanceHistory, entry, difference);
                    }
                    break;
                }
            }

            if (!dateIncluded) {
                // If transaction date doesn't exist in balance history ->
                // 1. Create a new instance of BalanceHistory and fill it with information from transaction.
                // 2. Add the new entry of BalanceHistory to balanceHistory.
                // 3. Save the updated CreditCard to the repository.
                BalanceHistory newEntry = new BalanceHistory();
                newEntry.setDate(transactionDate);
                newEntry.setBalance(transactionAmount); // step 1
                newEntry.setCreditCard(creditCard);
                balanceHistory.add(newEntry); // step 2
                creditCardRepository.save(creditCard); // step 3

                // Now, refill the gaps because this transaction possibly created a gap in BalanceHistory entries.
                // That is because it is outside the range of balanceHistory.
                fillBalanceHistoryGaps(balanceHistory, creditCard, creditCardRepository);

                // I have not called to update subsequent balances because I have no BalanceHistory to compare
                // balance value to. If it is preferable to call the function using the initial value of "0" to compare,
                // I would call:
                // updateSubsequentBalances(balanceHistory, newEntry, transactionAmount);
            }

            // Finally, sort balanceHistory in descending order based on date to accommodate all changes.
            balanceHistory.sort((entry1, entry2) -> entry2.getDate().compareTo(entry1.getDate()));

            for (BalanceHistory balHis : balanceHistory) {
                System.out.println(balHis);
            }
            // Save the updated CreditCard to the repository.
            creditCardRepository.save(creditCard);
        }
    }

    private static void addEntryForToday(List<BalanceHistory> balanceHistory, CreditCard creditCard) {
        if (balanceHistory == null) {
            // Technically, this should never happen because we never call addEntryForToday on an
            // empty balanceHistory, so I only checked this with a print statement.
            System.out.println("null balance!!");
        }
        LocalDate todayDate = LocalDate.now(); // set a variable with current date.

        // If there already exists a BalanceEntry corresponding today's date in balanceHistory, I don't
        // need to add another one.
        if (containsDate(balanceHistory, todayDate)) {
            return;
        }

        // We now have to properly create a BalanceHistory instance for today ->
        // 1. Set the instance todayEntry with the current LocalDate.
        // 2. Find the previous BalanceHistory.
        // 3. Set other data members of todayEntry using the information from previous BalanceHistory and add.

        BalanceHistory todayEntry = new BalanceHistory();
        todayEntry.setDate(todayDate); // step 1

        BalanceHistory previousBalance = null;
        // I search the balanceHistory list in order to look for the first entry with a date that is smaller than
        // todayDate, or the current date.
        // This operates off the belief that balanceHistory is properly sorted from largest to smallest date.
        // If not, I would have to sort it again here. ~1 line ->
        // balanceHistory.sort((entry1, entry2) -> entry2.getDate().compareTo(entry1.getDate()));
        for (BalanceHistory entry : balanceHistory) {
            if (entry.getDate().isBefore(todayDate)) {
                previousBalance = entry;
                break; // step 2
            }
        }

        if (previousBalance == null) {
            // This should also technically never happen because balanceHistory is not supposed to be null here,
            // so I only checked this with a print statement.
            System.out.println("null previous balance!!");
        }

        todayEntry.setBalance(previousBalance.getBalance());
        todayEntry.setCreditCard(creditCard); // Set the credit card
        balanceHistory.add(todayEntry); // step 3: Add the entry to the balance history
    }

    private static void fillBalanceHistoryGaps(List<BalanceHistory> balanceHistory, CreditCard creditCard, CreditCardRepository creditCardRepository) {
        // We don't need to check if balanceHistory is empty, because we already went through the null check and empty
        // list check prior. In case, we sort the list.
        balanceHistory.sort((entry1, entry2) -> entry2.getDate().compareTo(entry1.getDate()));

        // I create a new list that I will fill with BalanceHistories to add to my balanceHistory at the end in order
        // to prevent runtime issues that came up when I iterated through my list while simultaneously updating it.
        List<BalanceHistory> newEntries = new LinkedList<>();

        // Iterate over the balance history entries from most recent to the least recent.
        // We start with i = 1 because we compare the BalanceHistory at (i) with that at (i-1).
        for (int i = 1; i < balanceHistory.size(); i++) {
            BalanceHistory current = balanceHistory.get(i);
            BalanceHistory next = balanceHistory.get(i - 1); // note that this is more recent
            System.out.println("fillBalanceHistoryGaps: ");
            System.out.println(current);
            System.out.println(next);

            // Calculate the number of days between the previous and current entry.
            long daysBetween = ChronoUnit.DAYS.between(current.getDate(), next.getDate());

            // Enter if there are missing dates between the previous and current entry to update.
            if (daysBetween > 1) {
                // Fill the gaps by duplicating the current entry with missing dates for every single day that is
                // missing. The current is the less recent / older date, so we use the balance at current to update
                // all created BalanceHistory instances.
                for (int j = 1; j < daysBetween; j++) {
                    LocalDate missingDate = current.getDate().plusDays(j);
                    if (!containsDate(balanceHistory, missingDate)) {
                        BalanceHistory missingEntry = new BalanceHistory();
                        missingEntry.setDate(missingDate);
                        missingEntry.setBalance(current.getBalance());
                        missingEntry.setCreditCard(creditCard);
                        newEntries.add(missingEntry); // Add the missing entry to the new list
                    }
                }
            }
        }

        // Add new entries to balanceHistory after the iteration is complete
        balanceHistory.addAll(newEntries);

        // Save the credit card entity
        creditCardRepository.save(creditCard);
    }

    // Helper that checks if any of the BalanceHistories in balanceHistory matches a certain date.
    private static boolean containsDate(List<BalanceHistory> balanceHistory, LocalDate date) {
        for (BalanceHistory entry : balanceHistory) {
            if (entry.getDate().equals(date)) {
                return true;
            }
        }
        return false;
    }

    private void updateSubsequentBalances(List<BalanceHistory> balanceHistory, BalanceHistory currentEntry, double difference) {
        int currentIndex = balanceHistory.indexOf(currentEntry);
        // Iterate through the indices in the List of BalanceHistory instances BEFORE the "current entry" that was
        // requested to updated and add the difference to each of them. This is because "earlier" in the list
        // corresponds to more recent dates.
        for (int i = currentIndex - 1; i >= 0; i--) {
            BalanceHistory subsequentEntry = balanceHistory.get(i);
            double subsequentBalance = subsequentEntry.getBalance() + difference;
            subsequentEntry.setBalance(subsequentBalance);
        }
        // Note that I perform this traversal with BalanceHistory as a list, as lists are faster for traversals that
        // LinkedLists.
    }
}