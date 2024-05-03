package com.shepherdmoney.interviewproject.controller;
import com.shepherdmoney.interviewproject.model.*;
import com.shepherdmoney.interviewproject.repository.*;
import com.shepherdmoney.interviewproject.Service.*;

import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;


@RestController
public class CreditCardController {

    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private UserRepository userRepository;

    private final CreditCardService creditCardService;

    @Autowired
    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @PostMapping("/credit-card")
    public ResponseEntity<Integer> addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {
        // Initially, we check if the user repo contains the userid contained in payload. We return
        // a response code for NOT_FOUND if it isn't there.
        // When we call findById on the repo, Spring Data JPA will generate a query to find a User
        // by its integer key id.
        Optional<User> user = userRepository.findById(payload.getUserId());
        if (user.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Now that we've verified, we create a new credit card and set it with the appropriate
        // information that we retrieve from payload. We set the user using the user we found above
        // and add the new credit card to the user using the generated getter.
        CreditCard creditCard = new CreditCard();
        creditCard.setIssuanceBank(payload.getCardIssuanceBank());
        creditCard.setNumber(payload.getCardNumber());
        creditCard.setUser(user.get());

        // Save the credit card to the proper database.
        creditCard = creditCardRepository.save(creditCard);

        // Retrieve the generated credit card ID AFTER saving.
        Integer creditCardId = creditCard.getId();

        // Add the credit card to the user's list of cards and save that change to the user repo.
        user.get().getCards().add(creditCard);
        userRepository.save(user.get());

        return new ResponseEntity<Integer>(creditCardId, HttpStatus.OK);
    }

    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {
        // I like to cover this method with a try catch because it's meant to get all the credit cards and show the
        // info. If there is some internal issue, that will be reflected in the http status.
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                // If the user does not exist we show that the request was improper.
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            // We pull the list of credit cards from the specific user.
            List<CreditCard> creditCards = user.getCards();
            List<CreditCardView> creditCardViews = new ArrayList<>();
            if (creditCards.isEmpty()) {
                // If the user has no credit cards, we just return the empty list of CreditCardViews.
                return new ResponseEntity<>(creditCardViews, HttpStatus.OK);
            }

            // Now that we know the user has CreditCards, we can use the builder class to construct instances of
            // CreditCardView in a readable and concise manner from the list of CreditCards.
            for (CreditCard creditCard : creditCards) {
                CreditCardView creditCardView = CreditCardView.builder()
                        .issuanceBank(creditCard.getIssuanceBank())
                        .number(creditCard.getNumber())
                        .build();
                creditCardViews.add(creditCardView);
            }

            // Return the list of CreditCardViews
            return new ResponseEntity<>(creditCardViews, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/credit-card:user-id")
    public ResponseEntity<Integer> getUserIdForCreditCard(@RequestParam String creditCardNumber) {
        // Again, I wrapped this GET method in a try catch to differentiate internal server errors.
        try {
            CreditCard creditCard = creditCardRepository.findByNumber(creditCardNumber).orElse(null);

            if (creditCard != null) {
                User user = creditCard.getUser();
                // If a user is found, return the user ID in a 200 OK response
                if (user != null) {
                    return new ResponseEntity<>(user.getId(), HttpStatus.OK);
                } else {
                    // If no user is associated with the credit card, return a 400 Bad Request response without a body
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }
            } else {
                // If no credit card is found, return a 400 Bad Request response without a body
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/credit-card:update-balance")
    public ResponseEntity<String> postMethodName(@RequestBody UpdateBalancePayload[] payload) {

        // Make sure none of the transactions in the array of UpdateBalancePayloads are tied to invalid CreditCards
        // (aka if they are not in the database). If one of the transactions are invalid, we can immediately return a
        // BAD_REQUEST rather than making improper changes to our actual database.
        for (UpdateBalancePayload transaction : payload) {
            String creditCardNumber = transaction.getCreditCardNumber();
            CreditCard creditCard = creditCardRepository.findByNumber(creditCardNumber).orElse(null);
            if (creditCard == null) {
                return new ResponseEntity<>("Card doesn't exist", HttpStatus.BAD_REQUEST);
            }
        }

        // Delegate the logic to the CreditCard service layer. I did this only for this function specifically because
        // it has greater complexity than the others, but in the future it would be preferable to do all of the logic coding
        // inside a service file.
        creditCardService.updateBalanceHistory(payload);

        // Return a response indicating success
        return ResponseEntity.ok("Balance updated successfully");
    }

}
