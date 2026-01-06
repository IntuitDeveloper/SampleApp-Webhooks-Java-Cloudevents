package com.intuit.developer.sampleapp.webhooks.controllers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.intuit.ipp.core.Context;
import com.intuit.ipp.core.ServiceType;
import com.intuit.ipp.data.AccountBasedExpenseLineDetail;
import com.intuit.ipp.data.Bill;
import com.intuit.ipp.data.Customer;
import com.intuit.ipp.data.EmailAddress;
import com.intuit.ipp.data.Invoice;
import com.intuit.ipp.data.JournalEntry;
import com.intuit.ipp.data.JournalEntryLineDetail;
import com.intuit.ipp.data.Line;
import com.intuit.ipp.data.LineDetailTypeEnum;
import com.intuit.ipp.data.LinkedTxn;
import com.intuit.ipp.data.Payment;
import com.intuit.ipp.data.PaymentTypeEnum;
import com.intuit.ipp.data.PhysicalAddress;
import com.intuit.ipp.data.PostingTypeEnum;
import com.intuit.ipp.data.Purchase;
import com.intuit.ipp.data.ReferenceType;
import com.intuit.ipp.data.SalesItemLineDetail;
import com.intuit.ipp.data.TelephoneNumber;
import com.intuit.ipp.data.Vendor;
import com.intuit.ipp.exception.FMSException;
import com.intuit.ipp.security.OAuth2Authorizer;
import com.intuit.ipp.services.DataService;
import com.intuit.ipp.services.QueryResult;

import jakarta.servlet.http.HttpSession;

/**
 * REST Controller for creating QuickBooks entities
 * Uses QuickBooks Java SDK 6.5.2 DataService directly
 */
@RestController
@RequestMapping("/api/quickbooks")
public class QuickBooksEntityController {
    
    private static final Logger logger = LoggerFactory.getLogger(QuickBooksEntityController.class);
    
    @PostMapping("/customers")
    public ResponseEntity<Map<String, Object>> createCustomer(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String displayName = request.get("displayName");
            if (displayName == null || displayName.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Display name is required"));
            }
            
            Customer customer = new Customer();
            customer.setDisplayName(displayName);
            customer.setGivenName(request.get("givenName"));
            customer.setFamilyName(request.get("familyName"));
            
            if (request.get("email") != null && !request.get("email").isEmpty()) {
                EmailAddress email = new EmailAddress();
                email.setAddress(request.get("email"));
                customer.setPrimaryEmailAddr(email);
            }
            
            if (request.get("phone") != null && !request.get("phone").isEmpty()) {
                TelephoneNumber phone = new TelephoneNumber();
                phone.setFreeFormNumber(request.get("phone"));
                customer.setPrimaryPhone(phone);
            }
            
            if (request.get("companyName") != null && !request.get("companyName").isEmpty()) {
                customer.setCompanyName(request.get("companyName"));
            }
            
            if (request.get("street") != null || request.get("city") != null) {
                PhysicalAddress address = new PhysicalAddress();
                address.setLine1(request.get("street"));
                address.setCity(request.get("city"));
                address.setCountrySubDivisionCode(request.get("state"));
                address.setPostalCode(request.get("zip"));
                customer.setBillAddr(address);
            }
            
            Customer created = dataService.add(customer);
            
            logger.info("Customer created: {} (ID: {})", created.getDisplayName(), created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("displayName", created.getDisplayName());
            response.put("message", "Customer created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating customer: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error creating customer: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/vendors")
    public ResponseEntity<Map<String, Object>> createVendor(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String displayName = request.get("displayName");
            if (displayName == null || displayName.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Display name is required"));
            }
            
            Vendor vendor = new Vendor();
            vendor.setDisplayName(displayName);
            
            if (request.get("companyName") != null && !request.get("companyName").isEmpty()) {
                vendor.setCompanyName(request.get("companyName"));
            }
            
            vendor.setGivenName(request.get("givenName"));
            vendor.setFamilyName(request.get("familyName"));
            
            if (request.get("email") != null && !request.get("email").isEmpty()) {
                EmailAddress email = new EmailAddress();
                email.setAddress(request.get("email"));
                vendor.setPrimaryEmailAddr(email);
            }
            
            if (request.get("phone") != null && !request.get("phone").isEmpty()) {
                TelephoneNumber phone = new TelephoneNumber();
                phone.setFreeFormNumber(request.get("phone"));
                vendor.setPrimaryPhone(phone);
            }
            
            if (request.get("street") != null || request.get("city") != null) {
                PhysicalAddress address = new PhysicalAddress();
                address.setLine1(request.get("street"));
                address.setCity(request.get("city"));
                address.setCountrySubDivisionCode(request.get("state"));
                address.setPostalCode(request.get("zip"));
                vendor.setBillAddr(address);
            }
            
            Vendor created = dataService.add(vendor);
            
            logger.info("Vendor created: {} (ID: {})", created.getDisplayName(), created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("displayName", created.getDisplayName());
            response.put("message", "Vendor created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating vendor: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error creating vendor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/invoices")
    public ResponseEntity<Map<String, Object>> createInvoice(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            if (request.get("customerId") == null || request.get("customerId").toString().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Customer ID is required"));
            }
            
            String customerId = request.get("customerId").toString();
            String customerQuery = "SELECT * FROM Customer WHERE Id = '" + customerId + "'";
            QueryResult customerResult = dataService.executeQuery(customerQuery);
            if (customerResult.getEntities() == null || customerResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Customer not found with ID: " + customerId));
            }
            
            Invoice invoice = new Invoice();
            
            ReferenceType customerRef = new ReferenceType();
            customerRef.setValue(customerId);
            invoice.setCustomerRef(customerRef);
            
            Line line = new Line();
            line.setLineNum(new BigInteger("1"));
            line.setDetailType(LineDetailTypeEnum.SALES_ITEM_LINE_DETAIL);
            
            SalesItemLineDetail salesItemLineDetail = new SalesItemLineDetail();
            salesItemLineDetail.setQty(new BigDecimal(request.get("quantity").toString()));
            salesItemLineDetail.setUnitPrice(new BigDecimal(request.get("rate").toString()));
            
            line.setSalesItemLineDetail(salesItemLineDetail);
            line.setDescription(request.get("description").toString());
            line.setAmount(new BigDecimal(request.get("amount").toString()));
            
            List<Line> lines = new ArrayList<>();
            lines.add(line);
            invoice.setLine(lines);
            
            Invoice created = dataService.add(invoice);
            
            logger.info("Invoice created: ID {}", created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("docNumber", created.getDocNumber());
            response.put("totalAmt", created.getTotalAmt());
            response.put("message", "Invoice created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating invoice: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid number format. Please check quantity, rate, and amount."));
        } catch (Exception e) {
            logger.error("Unexpected error creating invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String customerId = request.get("customerId").toString();
            String invoiceId = request.get("invoiceId") != null ? request.get("invoiceId").toString() : null;
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            
            if (customerId == null || customerId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Customer ID is required"));
            }
            
            String customerQuery = "SELECT * FROM Customer WHERE Id = '" + customerId + "'";
            QueryResult customerResult = dataService.executeQuery(customerQuery);
            if (customerResult.getEntities() == null || customerResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Customer not found with ID: " + customerId));
            }
            
            Customer customer = (Customer) customerResult.getEntities().get(0);
            if (customer.isActive() != null && !customer.isActive()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Cannot create payment for inactive customer"));
            }
            
            if (invoiceId != null && !invoiceId.isEmpty()) {
                String invoiceQuery = "SELECT * FROM Invoice WHERE Id = '" + invoiceId + "'";
                QueryResult invoiceResult = dataService.executeQuery(invoiceQuery);
                
                if (invoiceResult.getEntities() == null || invoiceResult.getEntities().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", "Invoice not found with ID: " + invoiceId + ". Please select a valid invoice."));
                }
                
                Invoice invoice = (Invoice) invoiceResult.getEntities().get(0);
                
                if (!invoice.getCustomerRef().getValue().equals(customerId)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", "Invoice does not belong to the selected customer. Please select a matching invoice."));
                }
                
                if (invoice.getBalance() == null || invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", "Invoice has no outstanding balance. Cannot link payment to fully paid invoice."));
                }
                
                if (amount.compareTo(invoice.getBalance()) > 0) {
                    logger.warn("Payment amount ${} exceeds invoice balance ${}", amount, invoice.getBalance());
                }
            }
            
            Payment payment = new Payment();
            
            ReferenceType customerRef = new ReferenceType();
            customerRef.setValue(customerId);
            payment.setCustomerRef(customerRef);
            
            payment.setTotalAmt(amount);
            
            if (invoiceId != null && !invoiceId.isEmpty()) {
                Line line = new Line();
                line.setAmount(amount);
                
                List<LinkedTxn> linkedTxn = new ArrayList<>();
                LinkedTxn linkedTxnRef = new LinkedTxn();
                linkedTxnRef.setTxnId(invoiceId);
                linkedTxnRef.setTxnType("Invoice");
                linkedTxn.add(linkedTxnRef);
                line.setLinkedTxn(linkedTxn);
                
                List<Line> lines = new ArrayList<>();
                lines.add(line);
                payment.setLine(lines);
            }
            
            Payment created = dataService.add(payment);
            
            logger.info("Payment created: ID {}", created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("totalAmt", created.getTotalAmt());
            response.put("message", "Payment created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating payment: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error creating payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/customers/update")
    public ResponseEntity<Map<String, Object>> updateCustomer(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String customerId = request.get("customerId");
            String query = "SELECT * FROM Customer WHERE Id = '" + customerId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Customer not found"));
            }
            
            Customer customer = (Customer) queryResult.getEntities().get(0);
            
            if (request.get("displayName") != null && !request.get("displayName").isEmpty()) {
                customer.setDisplayName(request.get("displayName"));
            }
            if (request.get("givenName") != null) {
                customer.setGivenName(request.get("givenName"));
            }
            if (request.get("familyName") != null) {
                customer.setFamilyName(request.get("familyName"));
            }
            if (request.get("email") != null && !request.get("email").isEmpty()) {
                EmailAddress email = new EmailAddress();
                email.setAddress(request.get("email"));
                customer.setPrimaryEmailAddr(email);
            }
            if (request.get("phone") != null && !request.get("phone").isEmpty()) {
                TelephoneNumber phone = new TelephoneNumber();
                phone.setFreeFormNumber(request.get("phone"));
                customer.setPrimaryPhone(phone);
            }
            
            Customer updated = dataService.update(customer);
            
            logger.info("Customer updated: {} (ID: {})", updated.getDisplayName(), updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("displayName", updated.getDisplayName());
            response.put("message", "Customer updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating customer: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error updating customer: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/customers/delete")
    public ResponseEntity<Map<String, Object>> deleteCustomer(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String customerId = request.get("customerId");
            String query = "SELECT * FROM Customer WHERE Id = '" + customerId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Customer not found"));
            }
            
            Customer customer = (Customer) queryResult.getEntities().get(0);
            
            // QuickBooks doesn't support delete - make customer inactive instead
            customer.setActive(false);
            Customer updated = dataService.update(customer);
            
            logger.info("Customer made inactive: ID {}", updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("message", "Customer made inactive successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error deleting customer: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error deleting customer: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/vendors/update")
    public ResponseEntity<Map<String, Object>> updateVendor(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String vendorId = request.get("vendorId");
            String query = "SELECT * FROM Vendor WHERE Id = '" + vendorId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Vendor not found"));
            }
            
            Vendor vendor = (Vendor) queryResult.getEntities().get(0);
            
            if (request.get("displayName") != null && !request.get("displayName").isEmpty()) {
                vendor.setDisplayName(request.get("displayName"));
            }
            if (request.get("companyName") != null) {
                vendor.setCompanyName(request.get("companyName"));
            }
            if (request.get("givenName") != null) {
                vendor.setGivenName(request.get("givenName"));
            }
            if (request.get("familyName") != null) {
                vendor.setFamilyName(request.get("familyName"));
            }
            if (request.get("email") != null && !request.get("email").isEmpty()) {
                EmailAddress email = new EmailAddress();
                email.setAddress(request.get("email"));
                vendor.setPrimaryEmailAddr(email);
            }
            if (request.get("phone") != null && !request.get("phone").isEmpty()) {
                TelephoneNumber phone = new TelephoneNumber();
                phone.setFreeFormNumber(request.get("phone"));
                vendor.setPrimaryPhone(phone);
            }
            
            Vendor updated = dataService.update(vendor);
            
            logger.info("Vendor updated: {} (ID: {})", updated.getDisplayName(), updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("displayName", updated.getDisplayName());
            response.put("message", "Vendor updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating vendor: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error updating vendor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/vendors/delete")
    public ResponseEntity<Map<String, Object>> deleteVendor(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String vendorId = request.get("vendorId");
            String query = "SELECT * FROM Vendor WHERE Id = '" + vendorId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Vendor not found"));
            }
            
            Vendor vendor = (Vendor) queryResult.getEntities().get(0);
            
            vendor.setActive(false);
            Vendor updated = dataService.update(vendor);
            
            logger.info("Vendor made inactive: ID {}", updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("message", "Vendor made inactive successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error deleting vendor: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error deleting vendor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/invoices/update")
    public ResponseEntity<Map<String, Object>> updateInvoice(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String invoiceId = request.get("invoiceId").toString();
            String query = "SELECT * FROM Invoice WHERE Id = '" + invoiceId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Invoice not found"));
            }
            
            Invoice invoice = (Invoice) queryResult.getEntities().get(0);
            
            // Update line item if provided
            if (request.get("description") != null || request.get("quantity") != null || request.get("rate") != null) {
                Line line = invoice.getLine().get(0);
                
                if (request.get("description") != null) {
                    line.setDescription(request.get("description").toString());
                }
                
                if (request.get("quantity") != null && request.get("rate") != null) {
                    BigDecimal qty = new BigDecimal(request.get("quantity").toString());
                    BigDecimal rate = new BigDecimal(request.get("rate").toString());
                    
                    SalesItemLineDetail detail = line.getSalesItemLineDetail();
                    detail.setQty(qty);
                    detail.setUnitPrice(rate);
                    line.setSalesItemLineDetail(detail);
                    line.setAmount(qty.multiply(rate));
                }
            }
            
            Invoice updated = dataService.update(invoice);
            
            logger.info("Invoice updated: ID {}", updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("docNumber", updated.getDocNumber());
            response.put("message", "Invoice updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating invoice: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error updating invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/invoices/void")
    public ResponseEntity<Map<String, Object>> voidInvoice(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String invoiceId = request.get("invoiceId");
            String query = "SELECT * FROM Invoice WHERE Id = '" + invoiceId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Invoice not found"));
            }
            
            Invoice invoice = (Invoice) queryResult.getEntities().get(0);
            
            // Void the invoice using delete (which voids invoices in QuickBooks)
            Invoice voided = dataService.delete(invoice);
            
            logger.info("Invoice voided: ID {}", voided.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", voided.getId());
            response.put("message", "Invoice voided successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error voiding invoice: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error voiding invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/invoices/email")
    public ResponseEntity<Map<String, Object>> emailInvoice(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String invoiceId = request.get("invoiceId");
            String emailTo = request.get("emailTo");
            
            String query = "SELECT * FROM Invoice WHERE Id = '" + invoiceId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Invoice not found"));
            }
            
            Invoice invoice = (Invoice) queryResult.getEntities().get(0);
            
            // Set email address if provided
            if (emailTo != null && !emailTo.isEmpty()) {
                EmailAddress email = new EmailAddress();
                email.setAddress(emailTo);
                invoice.setBillEmail(email);
            }
            
            dataService.sendEmail(invoice, emailTo);
            
            logger.info("Invoice emailed: ID {}", invoice.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", invoice.getId());
            response.put("message", "Invoice emailed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error emailing invoice: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error emailing invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/payments/update")
    public ResponseEntity<Map<String, Object>> updatePayment(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String paymentId = request.get("paymentId").toString();
            String query = "SELECT * FROM Payment WHERE Id = '" + paymentId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Payment not found"));
            }
            
            Payment payment = (Payment) queryResult.getEntities().get(0);
            
            // Update payment amount if provided
            if (request.get("amount") != null) {
                payment.setTotalAmt(new BigDecimal(request.get("amount").toString()));
            }
            
            Payment updated = dataService.update(payment);
            
            logger.info("Payment updated: ID {}", updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("totalAmt", updated.getTotalAmt());
            response.put("message", "Payment updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating payment: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error updating payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/payments/void")
    public ResponseEntity<Map<String, Object>> voidPayment(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String paymentId = request.get("paymentId");
            String query = "SELECT * FROM Payment WHERE Id = '" + paymentId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Payment not found"));
            }
            
            Payment payment = (Payment) queryResult.getEntities().get(0);
            
            // Void the payment using delete (which voids payments in QuickBooks)
            Payment voided = dataService.delete(payment);
            
            logger.info("Payment voided: ID {}", voided.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", voided.getId());
            response.put("message", "Payment voided successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error voiding payment: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error voiding payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PostMapping("/payments/delete")
    public ResponseEntity<Map<String, Object>> deletePayment(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        
        try {
            DataService dataService = getDataService(session);
            
            String paymentId = request.get("paymentId");
            String query = "SELECT * FROM Payment WHERE Id = '" + paymentId + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Payment not found"));
            }
            
            Payment payment = (Payment) queryResult.getEntities().get(0);
            
            Payment deleted = dataService.delete(payment);
            
            logger.info("Payment deleted: ID {}", deleted.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", deleted.getId());
            response.put("message", "Payment deleted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error deleting payment: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error deleting payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> getPayments(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            // Fetch all active customers ONCE (much faster than N queries)
            String customerQuery = "SELECT Id, DisplayName FROM Customer WHERE Active = true MAXRESULTS 1000";
            QueryResult customerResult = dataService.executeQuery(customerQuery);
            
            // Build a map of active customer IDs to names for O(1) lookup
            Map<String, String> activeCustomers = new HashMap<>();
            if (customerResult.getEntities() != null) {
                for (Object entity : customerResult.getEntities()) {
                    Customer customer = (Customer) entity;
                    activeCustomers.put(customer.getId(), customer.getDisplayName());
                }
            }
            
            // Fetch all payments
            String paymentQuery = "SELECT * FROM Payment MAXRESULTS 100";
            QueryResult paymentResult = dataService.executeQuery(paymentQuery);
            
            List<Map<String, Object>> payments = new ArrayList<>();
            if (paymentResult.getEntities() != null) {
                for (Object entity : paymentResult.getEntities()) {
                    Payment payment = (Payment) entity;
                    
                    // Check if payment has a customer reference
                    if (payment.getCustomerRef() != null && payment.getCustomerRef().getValue() != null) {
                        String customerId = payment.getCustomerRef().getValue();
                        
                        // Only include if customer is active (O(1) lookup)
                        if (activeCustomers.containsKey(customerId)) {
                            Map<String, Object> paymentMap = new HashMap<>();
                            paymentMap.put("id", payment.getId());
                            paymentMap.put("totalAmt", payment.getTotalAmt());
                            paymentMap.put("txnDate", payment.getTxnDate());
                            paymentMap.put("customerName", activeCustomers.get(customerId));
                            payments.add(paymentMap);
                        }
                    } else {
                        // Include payments without customer reference
                        Map<String, Object> paymentMap = new HashMap<>();
                        paymentMap.put("id", payment.getId());
                        paymentMap.put("totalAmt", payment.getTotalAmt());
                        paymentMap.put("txnDate", payment.getTxnDate());
                        payments.add(paymentMap);
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "payments", payments));
            
        } catch (Exception e) {
            logger.error("Error fetching payments: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> getCustomers(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Customer MAXRESULTS 100";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, String>> customers = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    Customer customer = (Customer) entity;
                    Map<String, String> customerMap = new HashMap<>();
                    customerMap.put("id", customer.getId());
                    customerMap.put("displayName", customer.getDisplayName());
                    customers.add(customerMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "customers", customers));
            
        } catch (Exception e) {
            logger.error("Error fetching customers: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @GetMapping("/vendors")
    public ResponseEntity<Map<String, Object>> getVendors(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Vendor MAXRESULTS 100";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, String>> vendors = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    Vendor vendor = (Vendor) entity;
                    Map<String, String> vendorMap = new HashMap<>();
                    vendorMap.put("id", vendor.getId());
                    vendorMap.put("displayName", vendor.getDisplayName());
                    vendors.add(vendorMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "vendors", vendors));
            
        } catch (Exception e) {
            logger.error("Error fetching vendors: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @GetMapping("/accounts/expense")
    public ResponseEntity<Map<String, Object>> getExpenseAccounts(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Account WHERE AccountType = 'Expense' MAXRESULTS 100";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, String>> accounts = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    com.intuit.ipp.data.Account account = (com.intuit.ipp.data.Account) entity;
                    Map<String, String> accountMap = new HashMap<>();
                    accountMap.put("id", account.getId());
                    accountMap.put("name", account.getName());
                    accountMap.put("fullyQualifiedName", account.getFullyQualifiedName());
                    accounts.add(accountMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "accounts", accounts));
            
        } catch (Exception e) {
            logger.error("Error fetching expense accounts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @GetMapping("/accounts/purchase")
    public ResponseEntity<Map<String, Object>> getPurchaseAccounts(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            // Purchase transactions support: Expense, Other Expense, Cost of Goods Sold
            String query = "SELECT * FROM Account WHERE AccountType IN ('Expense', 'Other Expense', 'Cost of Goods Sold') MAXRESULTS 100";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, String>> accounts = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    com.intuit.ipp.data.Account account = (com.intuit.ipp.data.Account) entity;
                    Map<String, String> accountMap = new HashMap<>();
                    accountMap.put("id", account.getId());
                    accountMap.put("name", account.getName());
                    accountMap.put("fullyQualifiedName", account.getFullyQualifiedName());
                    accountMap.put("accountType", account.getAccountType() != null ? account.getAccountType().value() : "");
                    accounts.add(accountMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "accounts", accounts));
            
        } catch (Exception e) {
            logger.error("Error fetching purchase accounts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> getInvoices(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            // Fetch all active customers ONCE (much faster than N queries)
            String customerQuery = "SELECT Id, DisplayName FROM Customer WHERE Active = true MAXRESULTS 1000";
            QueryResult customerResult = dataService.executeQuery(customerQuery);
            
            // Build a map of active customer IDs to names for O(1) lookup
            Map<String, String> activeCustomers = new HashMap<>();
            if (customerResult.getEntities() != null) {
                for (Object entity : customerResult.getEntities()) {
                    Customer customer = (Customer) entity;
                    activeCustomers.put(customer.getId(), customer.getDisplayName());
                }
            }
            
            // Fetch all invoices
            String invoiceQuery = "SELECT * FROM Invoice MAXRESULTS 100";
            QueryResult invoiceResult = dataService.executeQuery(invoiceQuery);
            
            List<Map<String, Object>> invoices = new ArrayList<>();
            if (invoiceResult.getEntities() != null) {
                for (Object entity : invoiceResult.getEntities()) {
                    Invoice invoice = (Invoice) entity;
                    
                    // Check if invoice has a customer reference
                    if (invoice.getCustomerRef() != null && invoice.getCustomerRef().getValue() != null) {
                        String customerId = invoice.getCustomerRef().getValue();
                        
                        // Only include if customer is active (O(1) lookup)
                        if (activeCustomers.containsKey(customerId)) {
                            Map<String, Object> invoiceMap = new HashMap<>();
                            invoiceMap.put("id", invoice.getId());
                            invoiceMap.put("docNumber", invoice.getDocNumber());
                            invoiceMap.put("totalAmt", invoice.getTotalAmt());
                            invoiceMap.put("balance", invoice.getBalance());
                            invoiceMap.put("customerName", activeCustomers.get(customerId));
                            invoices.add(invoiceMap);
                        }
                    } else {
                        // Include invoices without customer reference
                        Map<String, Object> invoiceMap = new HashMap<>();
                        invoiceMap.put("id", invoice.getId());
                        invoiceMap.put("docNumber", invoice.getDocNumber());
                        invoiceMap.put("totalAmt", invoice.getTotalAmt());
                        invoiceMap.put("balance", invoice.getBalance());
                        invoices.add(invoiceMap);
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "invoices", invoices));
            
        } catch (Exception e) {
            logger.error("Error fetching invoices: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    // ===========================
    // BILL OPERATIONS
    // ===========================
    
    @PostMapping("/bills")
    public ResponseEntity<Map<String, Object>> createBill(@RequestBody Map<String, Object> billData, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            if (billData.get("vendorId") == null || billData.get("vendorId").toString().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Vendor ID is required"));
            }
            
            if (billData.get("amount") == null || billData.get("amount").toString().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Amount is required"));
            }
            
            Bill bill = new Bill();
            bill.setVendorRef(createReferenceType((String) billData.get("vendorId")));
            bill.setTxnDate(new Date());
            bill.setDueDate(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));
            
            Line line = new Line();
            line.setDetailType(LineDetailTypeEnum.ACCOUNT_BASED_EXPENSE_LINE_DETAIL);
            line.setAmount(new BigDecimal((String) billData.get("amount")));
            line.setDescription((String) billData.get("description"));
            
            AccountBasedExpenseLineDetail lineDetail = new AccountBasedExpenseLineDetail();
            lineDetail.setAccountRef(createReferenceType((String) billData.get("expenseAccountId")));
            line.setAccountBasedExpenseLineDetail(lineDetail);
            
            bill.setLine(Collections.singletonList(line));
            
            Bill created = dataService.add(bill);
            
            logger.info("Bill created: ID {}", created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("docNumber", created.getDocNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating bill: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error creating bill: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PutMapping("/bills/{id}")
    public ResponseEntity<Map<String, Object>> updateBill(@PathVariable String id, @RequestBody Map<String, Object> billData, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Bill WHERE Id = '" + id + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Bill not found"));
            }
            
            Bill bill = (Bill) queryResult.getEntities().get(0);
            
            // Update line amount/description
            if (bill.getLine() != null && !bill.getLine().isEmpty()) {
                Line line = bill.getLine().get(0);
                line.setAmount(new BigDecimal((String) billData.get("amount")));
                line.setDescription((String) billData.get("description"));
            }
            
            Bill updated = dataService.update(bill);
            
            logger.info("Bill updated: ID {}", updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("docNumber", updated.getDocNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating bill: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error updating bill: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/bills/{id}")
    public ResponseEntity<Map<String, Object>> deleteBill(@PathVariable String id, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Bill WHERE Id = '" + id + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Bill not found"));
            }
            
            Bill bill = (Bill) queryResult.getEntities().get(0);
            Bill deleted = dataService.delete(bill);
            
            logger.info("Bill deleted: ID {}", deleted.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", deleted.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error deleting bill: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error deleting bill: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @GetMapping("/bills")
    public ResponseEntity<Map<String, Object>> getBills(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Bill MAXRESULTS 50";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, Object>> bills = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    Bill bill = (Bill) entity;
                    Map<String, Object> billMap = new HashMap<>();
                    billMap.put("id", bill.getId());
                    billMap.put("docNumber", bill.getDocNumber());
                    billMap.put("txnDate", bill.getTxnDate());
                    billMap.put("balance", bill.getBalance());
                    if (bill.getVendorRef() != null) {
                        billMap.put("vendorName", bill.getVendorRef().getName());
                    }
                    bills.add(billMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "bills", bills));
            
        } catch (Exception e) {
            logger.error("Error fetching bills: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    // ===========================
    // JOURNAL ENTRY OPERATIONS
    // ===========================
    
    @PostMapping("/journalentries")
    public ResponseEntity<Map<String, Object>> createJournalEntry(@RequestBody Map<String, Object> journalEntryData, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            if (journalEntryData.get("amount") == null || journalEntryData.get("amount").toString().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Amount is required"));
            }
            
            if (journalEntryData.get("debitAccountId") == null || journalEntryData.get("debitAccountId").toString().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Debit account is required"));
            }
            
            if (journalEntryData.get("creditAccountId") == null || journalEntryData.get("creditAccountId").toString().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Credit account is required"));
            }
            
            JournalEntry journalEntry = new JournalEntry();
            journalEntry.setTxnDate(new Date());
            
            Line debitLine = new Line();
            debitLine.setDetailType(LineDetailTypeEnum.JOURNAL_ENTRY_LINE_DETAIL);
            debitLine.setAmount(new BigDecimal((String) journalEntryData.get("amount")));
            debitLine.setDescription((String) journalEntryData.get("description"));
            
            JournalEntryLineDetail debitDetail = new JournalEntryLineDetail();
            debitDetail.setPostingType(PostingTypeEnum.DEBIT);
            debitDetail.setAccountRef(createReferenceType((String) journalEntryData.get("debitAccountId")));
            debitLine.setJournalEntryLineDetail(debitDetail);
            
            Line creditLine = new Line();
            creditLine.setDetailType(LineDetailTypeEnum.JOURNAL_ENTRY_LINE_DETAIL);
            creditLine.setAmount(new BigDecimal((String) journalEntryData.get("amount")));
            creditLine.setDescription((String) journalEntryData.get("description"));
            
            JournalEntryLineDetail creditDetail = new JournalEntryLineDetail();
            creditDetail.setPostingType(PostingTypeEnum.CREDIT);
            creditDetail.setAccountRef(createReferenceType((String) journalEntryData.get("creditAccountId")));
            creditLine.setJournalEntryLineDetail(creditDetail);
            
            journalEntry.setLine(Arrays.asList(debitLine, creditLine));
            
            JournalEntry created = dataService.add(journalEntry);
            
            logger.info("Journal Entry created: {} (ID: {})", created.getDocNumber(), created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("docNumber", created.getDocNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating journal entry: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error creating journal entry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PutMapping("/journalentries/{id}")
    public ResponseEntity<Map<String, Object>> updateJournalEntry(@PathVariable String id, @RequestBody Map<String, Object> journalEntryData, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM JournalEntry WHERE Id = '" + id + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Journal Entry not found"));
            }
            
            JournalEntry journalEntry = (JournalEntry) queryResult.getEntities().get(0);
            
            // Update amount on both lines
            BigDecimal newAmount = new BigDecimal((String) journalEntryData.get("amount"));
            for (Line line : journalEntry.getLine()) {
                line.setAmount(newAmount);
                if (journalEntryData.get("description") != null) {
                    line.setDescription((String) journalEntryData.get("description"));
                }
            }
            
            JournalEntry updated = dataService.update(journalEntry);
            
            logger.info("Journal Entry updated: {} (ID: {})", updated.getDocNumber(), updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("docNumber", updated.getDocNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating journal entry: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error updating journal entry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/journalentries/{id}")
    public ResponseEntity<Map<String, Object>> deleteJournalEntry(@PathVariable String id, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM JournalEntry WHERE Id = '" + id + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Journal Entry not found"));
            }
            
            JournalEntry journalEntry = (JournalEntry) queryResult.getEntities().get(0);
            JournalEntry deleted = dataService.delete(journalEntry);
            
            logger.info("Journal Entry deleted: ID {}", deleted.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", deleted.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error deleting journal entry: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error deleting journal entry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @GetMapping("/journalentries")
    public ResponseEntity<Map<String, Object>> getJournalEntries(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM JournalEntry MAXRESULTS 50";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, Object>> journalEntries = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    JournalEntry je = (JournalEntry) entity;
                    Map<String, Object> jeMap = new HashMap<>();
                    jeMap.put("id", je.getId());
                    jeMap.put("docNumber", je.getDocNumber());
                    jeMap.put("txnDate", je.getTxnDate());
                    // Get total amount from first line
                    if (je.getLine() != null && !je.getLine().isEmpty()) {
                        jeMap.put("amount", je.getLine().get(0).getAmount());
                    }
                    journalEntries.add(jeMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "journalEntries", journalEntries));
            
        } catch (Exception e) {
            logger.error("Error fetching journal entries: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    // ===========================
    // PURCHASE (EXPENSE) OPERATIONS
    // ===========================
    
    @PostMapping("/purchases")
    public ResponseEntity<Map<String, Object>> createPurchase(@RequestBody Map<String, Object> purchaseData, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            Purchase purchase = new Purchase();
            purchase.setTxnDate(new Date());
            purchase.setPaymentType(PaymentTypeEnum.CASH);
            
            // Note: DocNumber is optional for Purchase transactions and not auto-generated by QuickBooks
            // We'll rely on the Purchase ID for identification
            
            // Find a valid payment account (Bank or Cash) for the Purchase level
            // Purchase.AccountRef must be Bank/Cash/Credit Card type, not Expense type
            String paymentAccountQuery = "SELECT * FROM Account WHERE AccountType IN ('Bank', 'Other Current Asset') AND Name LIKE '%Cash%' MAXRESULTS 1";
            QueryResult accountResult = dataService.executeQuery(paymentAccountQuery);
            
            if (accountResult.getEntities() == null || accountResult.getEntities().isEmpty()) {
                // Fallback: try to find any Bank account
                paymentAccountQuery = "SELECT * FROM Account WHERE AccountType = 'Bank' MAXRESULTS 1";
                accountResult = dataService.executeQuery(paymentAccountQuery);
                
                if (accountResult.getEntities() == null || accountResult.getEntities().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", "No valid payment account (Bank/Cash) found. Please create a bank or cash account in QuickBooks."));
                }
            }
            
            com.intuit.ipp.data.Account paymentAccount = (com.intuit.ipp.data.Account) accountResult.getEntities().get(0);
            ReferenceType paymentAccountRef = createReferenceType(paymentAccount.getId());
            purchase.setAccountRef(paymentAccountRef);
            
            // Create expense line with the expense account
            Line expenseLine = new Line();
            expenseLine.setDetailType(LineDetailTypeEnum.ACCOUNT_BASED_EXPENSE_LINE_DETAIL);
            expenseLine.setAmount(new BigDecimal((String) purchaseData.get("amount")));
            expenseLine.setDescription((String) purchaseData.get("description"));
            
            // Set expense account reference in the line detail
            AccountBasedExpenseLineDetail expenseDetail = new AccountBasedExpenseLineDetail();
            ReferenceType expenseAccountRef = createReferenceType((String) purchaseData.get("accountId"));
            expenseDetail.setAccountRef(expenseAccountRef);
            expenseLine.setAccountBasedExpenseLineDetail(expenseDetail);
            
            purchase.setLine(Collections.singletonList(expenseLine));
            
            // Set entity reference if provided (vendor)
            if (purchaseData.get("vendorId") != null && !((String) purchaseData.get("vendorId")).isEmpty()) {
                purchase.setEntityRef(createReferenceType((String) purchaseData.get("vendorId")));
            }
            
            Purchase created = dataService.add(purchase);
            
            logger.info("Purchase created: {} (ID: {})", created.getDocNumber(), created.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", created.getId());
            response.put("docNumber", created.getDocNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error creating purchase: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error creating purchase: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @PutMapping("/purchases/{id}")
    public ResponseEntity<Map<String, Object>> updatePurchase(@PathVariable String id, @RequestBody Map<String, Object> purchaseData, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Purchase WHERE Id = '" + id + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Purchase not found"));
            }
            
            Purchase purchase = (Purchase) queryResult.getEntities().get(0);
            
            // Update amount on the line
            if (purchase.getLine() != null && !purchase.getLine().isEmpty()) {
                Line line = purchase.getLine().get(0);
                line.setAmount(new BigDecimal((String) purchaseData.get("amount")));
                if (purchaseData.get("description") != null) {
                    line.setDescription((String) purchaseData.get("description"));
                }
            }
            
            Purchase updated = dataService.update(purchase);
            
            logger.info("Purchase updated: {} (ID: {})", updated.getDocNumber(), updated.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", updated.getId());
            response.put("docNumber", updated.getDocNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error updating purchase: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid amount format"));
        } catch (Exception e) {
            logger.error("Unexpected error updating purchase: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/purchases/{id}")
    public ResponseEntity<Map<String, Object>> deletePurchase(@PathVariable String id, HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Purchase WHERE Id = '" + id + "'";
            QueryResult queryResult = dataService.executeQuery(query);
            
            if (queryResult.getEntities() == null || queryResult.getEntities().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Purchase not found"));
            }
            
            Purchase purchase = (Purchase) queryResult.getEntities().get(0);
            Purchase deleted = dataService.delete(purchase);
            
            logger.info("Purchase deleted: ID {}", deleted.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", deleted.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (FMSException e) {
            logger.error("Error deleting purchase: {}", e.getMessage(), e);
            String userMessage = parseQuickBooksError(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", userMessage));
        } catch (Exception e) {
            logger.error("Unexpected error deleting purchase: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    @GetMapping("/purchases")
    public ResponseEntity<Map<String, Object>> getPurchases(HttpSession session) {
        try {
            DataService dataService = getDataService(session);
            
            String query = "SELECT * FROM Purchase MAXRESULTS 50";
            QueryResult queryResult = dataService.executeQuery(query);
            
            List<Map<String, Object>> purchases = new ArrayList<>();
            if (queryResult.getEntities() != null) {
                for (Object entity : queryResult.getEntities()) {
                    Purchase purchase = (Purchase) entity;
                    Map<String, Object> purchaseMap = new HashMap<>();
                    purchaseMap.put("id", purchase.getId());
                    purchaseMap.put("docNumber", purchase.getDocNumber());
                    purchaseMap.put("txnDate", purchase.getTxnDate());
                    // Get total amount from first line
                    if (purchase.getLine() != null && !purchase.getLine().isEmpty()) {
                        purchaseMap.put("amount", purchase.getLine().get(0).getAmount());
                    }
                    purchases.add(purchaseMap);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "purchases", purchases));
            
        } catch (Exception e) {
            logger.error("Error fetching purchases: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    private DataService getDataService(HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated. Please connect to QuickBooks first.");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        
        return new DataService(context);
    }
    
    /**
     * Helper method to create ReferenceType
     */
    private ReferenceType createReferenceType(String id) {
        ReferenceType ref = new ReferenceType();
        ref.setValue(id);
        return ref;
    }
    
    /**
     * Parse QuickBooks FMSException to user-friendly error messages
     */
    private String parseQuickBooksError(FMSException e) {
        String errorMessage = e.getMessage();
        
        if (errorMessage.contains("TxnID Cannot Be Linked")) {
            return "The invoice you selected cannot be linked to this payment. This may happen if: 1) The invoice doesn't exist, 2) The invoice doesn't belong to this customer, or 3) The invoice has already been fully paid. Please verify the invoice and try again.";
        }
        
        if (errorMessage.contains("Customer assigned to this transaction has been deleted")) {
            return "Cannot modify this transaction - the associated customer is inactive or deleted. Please restore the customer first or choose a different transaction.";
        }
        
        if (errorMessage.contains("Vendor assigned to this transaction has been deleted")) {
            return "Cannot modify this transaction - the associated vendor is inactive or deleted. Please restore the vendor first or choose a different transaction.";
        }
        
        if (errorMessage.contains("Invalid Reference Id") || errorMessage.contains("Invalid reference id")) {
            return "Invalid reference - the entity you're trying to modify may have been deleted or is in an invalid state.";
        }
        
        if (errorMessage.contains("Unsupported Operation")) {
            return "This operation is not supported by QuickBooks for this entity type.";
        }
        
        if (errorMessage.contains("Stale Object Error") || errorMessage.contains("stale object")) {
            return "This entity has been modified by another user or in another window. Please refresh and try again.";
        }
        
        if (errorMessage.contains("Authentication failed") || errorMessage.contains("AuthenticationFailed")) {
            return "Authentication failed. Your QuickBooks session may have expired. Please reconnect to QuickBooks.";
        }
        
        if (errorMessage.contains("Permission denied") || errorMessage.contains("AuthorizationFailed")) {
            return "Permission denied. Your QuickBooks account doesn't have permission for this operation.";
        }
        
        if (errorMessage.contains("Required param missing")) {
            return "Missing required field. Please ensure all required information is provided.";
        }
        
        if (errorMessage.contains("Duplicate Name Exists Error")) {
            return "An entity with this name already exists. Please use a different name.";
        }
        
        if (errorMessage.contains("Business Validation Error")) {
            return "Business validation error: " + extractErrorDetail(errorMessage);
        }
        
        if (errorMessage.contains("The request sent by the client was syntactically incorrect")) {
            return "Invalid request format. Please check your input and try again.";
        }
        
        if (errorMessage.contains("Object Not Found")) {
            return "The requested entity was not found. It may have been deleted.";
        }
        
        if (errorMessage.contains("ERROR CODE:")) {
            String errorCode = extractErrorCode(errorMessage);
            String errorDetail = extractErrorDetail(errorMessage);
            if (errorDetail != null && !errorDetail.isEmpty()) {
                return errorDetail;
            }
            return "QuickBooks Error " + errorCode + ": " + errorMessage;
        }
        
        if (errorMessage.contains("ERROR DETAIL:")) {
            String[] parts = errorMessage.split("ERROR DETAIL:");
            if (parts.length > 1) {
                String detail = parts[1].split("MORE ERROR DETAIL")[0].trim();
                return detail.replaceAll(",\\s*$", "");
            }
        }
        
        return errorMessage;
    }
    
    /**
     * Extract error code from QuickBooks error message
     */
    private String extractErrorCode(String errorMessage) {
        if (errorMessage.contains("ERROR CODE:")) {
            String[] parts = errorMessage.split("ERROR CODE:");
            if (parts.length > 1) {
                String codePart = parts[1].split(",")[0].trim();
                return codePart;
            }
        }
        return "Unknown";
    }
    
    /**
     * Extract error detail from QuickBooks error message
     */
    private String extractErrorDetail(String errorMessage) {
        if (errorMessage.contains("ERROR DETAIL:")) {
            String[] parts = errorMessage.split("ERROR DETAIL:");
            if (parts.length > 1) {
                String detail = parts[1].split("MORE ERROR DETAIL")[0].trim();
                return detail.replaceAll(",\\s*$", "").replaceAll(":\\s*\\d+$", "");
            }
        }
        return null;
    }
}
