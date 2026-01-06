// Entity creation functions for QuickBooks

function openModal(modalId) {
    console.log('openModal called with modalId:', modalId);
    const modal = document.getElementById(modalId);
    console.log('Modal element found:', modal !== null);
    
    if (modal) {
        modal.style.display = 'flex';
        
        if (modalId === 'invoiceModal' || modalId === 'paymentModal') {
            console.log('Loading customers for invoice/payment');
            loadCustomers();
        } else if (modalId === 'updateCustomerModal') {
            loadCustomersForAction('update');
        } else if (modalId === 'deleteCustomerModal') {
            loadCustomersForAction('delete');
        } else if (modalId === 'updateVendorModal') {
            loadVendorsForAction('update');
        } else if (modalId === 'deleteVendorModal') {
            loadVendorsForAction('delete');
        } else if (modalId === 'updateInvoiceModal' || modalId === 'voidInvoiceModal' || modalId === 'emailInvoiceModal') {
            loadInvoicesForAction();
        } else if (modalId === 'updatePaymentModal' || modalId === 'voidPaymentModal' || modalId === 'deletePaymentModal') {
            loadPaymentsForAction();
        } else if (modalId === 'billModal') {
            console.log('BILL MODAL DETECTED - calling loadVendors() and loadExpenseAccounts()');
            loadVendors();
            loadExpenseAccounts();
        } else if (modalId === 'updateBillModal' || modalId === 'deleteBillModal') {
            console.log('UPDATE/DELETE BILL MODAL - calling loadBillsForAction()');
            loadBillsForAction();
        } else if (modalId === 'journalEntryModal') {
            console.log('JOURNAL ENTRY MODAL DETECTED - calling loadAccountsForJournalEntry()');
            loadAccountsForJournalEntry();
        } else if (modalId === 'updateJournalEntryModal' || modalId === 'deleteJournalEntryModal') {
            console.log('UPDATE/DELETE JOURNAL ENTRY MODAL - calling loadJournalEntriesForAction()');
            loadJournalEntriesForAction();
        } else if (modalId === 'purchaseModal') {
            console.log('PURCHASE MODAL DETECTED - calling loadAccountsForPurchase() and loadVendorsForPurchase()');
            loadAccountsForPurchase();
            loadVendorsForPurchase();
        } else if (modalId === 'updatePurchaseModal' || modalId === 'deletePurchaseModal') {
            console.log('UPDATE/DELETE PURCHASE MODAL - calling loadPurchasesForAction()');
            loadPurchasesForAction();
        }
    } else {
        console.error('Modal element not found for id:', modalId);
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
        const form = modal.querySelector('form');
        if (form) {
            form.reset();
        }
    }
}

window.onclick = function(event) {
    if (event.target.classList.contains('modal')) {
        event.target.style.display = 'none';
    }
}

function showStatus(message, isError = false) {
    const statusDiv = document.getElementById('statusMessage');
    statusDiv.textContent = message;
    statusDiv.className = 'status-message ' + (isError ? 'error' : 'success');
    statusDiv.style.display = 'block';
    
    setTimeout(() => {
        statusDiv.style.display = 'none';
    }, 5000);
}

function generateCustomer() {
    const names = ['John', 'Jane', 'Bob', 'Alice', 'Charlie', 'Diana'];
    const lastNames = ['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia'];
    const firstName = names[Math.floor(Math.random() * names.length)];
    const lastName = lastNames[Math.floor(Math.random() * lastNames.length)];
    
    document.getElementById('customerGivenName').value = firstName;
    document.getElementById('customerFamilyName').value = lastName;
    document.getElementById('customerDisplayName').value = `${firstName} ${lastName}`;
    document.getElementById('customerEmail').value = `${firstName.toLowerCase()}.${lastName.toLowerCase()}@example.com`;
    document.getElementById('customerPhone').value = '555-' + Math.floor(Math.random() * 9000 + 1000);
    document.getElementById('customerCompany').value = `${lastName} Corp`;
    document.getElementById('customerStreet').value = Math.floor(Math.random() * 9999) + ' Main St';
    document.getElementById('customerCity').value = 'San Francisco';
    document.getElementById('customerState').value = 'CA';
    document.getElementById('customerZip').value = '94' + Math.floor(Math.random() * 900 + 100);
}

function generateVendor() {
    const companies = ['Acme', 'TechCorp', 'Global', 'Premier', 'Elite', 'Pro'];
    const types = ['Solutions', 'Services', 'Systems', 'Consulting', 'Industries'];
    const companyName = companies[Math.floor(Math.random() * companies.length)] + ' ' + 
                       types[Math.floor(Math.random() * types.length)];
    
    document.getElementById('vendorDisplayName').value = companyName;
    document.getElementById('vendorCompanyName').value = companyName;
    document.getElementById('vendorGivenName').value = 'Contact';
    document.getElementById('vendorFamilyName').value = 'Person';
    document.getElementById('vendorEmail').value = 'contact@' + companyName.toLowerCase().replace(' ', '') + '.com';
    document.getElementById('vendorPhone').value = '555-' + Math.floor(Math.random() * 9000 + 1000);
}

function generateInvoice() {
    const descriptions = ['Consulting Services', 'Software Development', 'Design Work', 'Marketing Services', 'IT Support'];
    document.getElementById('invoiceDescription').value = descriptions[Math.floor(Math.random() * descriptions.length)];
    document.getElementById('invoiceQuantity').value = Math.floor(Math.random() * 10 + 1);
    document.getElementById('invoiceRate').value = (Math.floor(Math.random() * 200 + 50)).toFixed(2);
    updateInvoiceAmount();
}

function generatePayment() {
    document.getElementById('paymentAmount').value = (Math.floor(Math.random() * 500 + 50)).toFixed(2);
}

function updateInvoiceAmount() {
    const qty = parseFloat(document.getElementById('invoiceQuantity').value) || 0;
    const rate = parseFloat(document.getElementById('invoiceRate').value) || 0;
    const amount = qty * rate;
    document.getElementById('invoiceAmount').textContent = '$' + amount.toFixed(2);
}

function loadCustomers() {
    fetch('/api/quickbooks/customers', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.customers) {
            const invoiceSelect = document.getElementById('invoiceCustomer');
            if (invoiceSelect) {
                invoiceSelect.innerHTML = '<option value="">Select a customer...</option>';
                data.customers.forEach(customer => {
                    const option = document.createElement('option');
                    option.value = customer.id;
                    option.textContent = customer.displayName;
                    invoiceSelect.appendChild(option);
                });
            }
            
            const paymentSelect = document.getElementById('paymentCustomer');
            if (paymentSelect) {
                paymentSelect.innerHTML = '<option value="">Select a customer...</option>';
                data.customers.forEach(customer => {
                    const option = document.createElement('option');
                    option.value = customer.id;
                    option.textContent = customer.displayName;
                    paymentSelect.appendChild(option);
                });
            }
        }
    })
    .catch(error => {
        console.error('Error loading customers:', error);
    });
}

function loadInvoicesForPayment() {
    const customerId = document.getElementById('paymentCustomer').value;
    const invoiceSelect = document.getElementById('paymentInvoice');
    
    if (!customerId) {
        invoiceSelect.innerHTML = '<option value="">Select customer first...</option>';
        return;
    }
    
    invoiceSelect.innerHTML = '<option value="">Loading invoices...</option>';
    
    fetch('/api/quickbooks/invoices', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.invoices) {
            invoiceSelect.innerHTML = '<option value="">Unapplied payment (no invoice)</option>';
            data.invoices.forEach(invoice => {
                if (invoice.balance && parseFloat(invoice.balance) > 0) {
                    const option = document.createElement('option');
                    option.value = invoice.id;
                    option.textContent = `Invoice #${invoice.docNumber} - Balance: $${invoice.balance}`;
                    invoiceSelect.appendChild(option);
                }
            });
        }
    })
    .catch(error => {
        console.error('Error loading invoices:', error);
        invoiceSelect.innerHTML = '<option value="">Error loading invoices</option>';
    });
}

function submitCustomer(event) {
    event.preventDefault();
    
    const customerData = {
        displayName: document.getElementById('customerDisplayName').value,
        givenName: document.getElementById('customerGivenName').value,
        familyName: document.getElementById('customerFamilyName').value,
        email: document.getElementById('customerEmail').value,
        phone: document.getElementById('customerPhone').value,
        companyName: document.getElementById('customerCompany').value,
        street: document.getElementById('customerStreet').value,
        city: document.getElementById('customerCity').value,
        state: document.getElementById('customerState').value,
        zip: document.getElementById('customerZip').value
    };
    
    fetch('/api/quickbooks/customers', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(customerData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Customer "${data.displayName}" created successfully.`);
            closeModal('customerModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating customer: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitUpdateCustomer(event) {
    event.preventDefault();
    
    const customerData = {
        customerId: document.getElementById('updateCustomerId').value,
        displayName: document.getElementById('updateCustomerDisplayName').value,
        givenName: document.getElementById('updateCustomerGivenName').value,
        familyName: document.getElementById('updateCustomerFamilyName').value,
        email: document.getElementById('updateCustomerEmail').value,
        phone: document.getElementById('updateCustomerPhone').value
    };
    
    fetch('/api/quickbooks/customers/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(customerData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Customer "${data.displayName}" updated successfully.`);
            closeModal('updateCustomerModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating customer: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitDeleteCustomer(event) {
    event.preventDefault();
    
    const customerId = document.getElementById('deleteCustomerId').value;
    
    if (!confirm('Are you sure you want to make this customer inactive?')) {
        return;
    }
    
    fetch('/api/quickbooks/customers/delete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ customerId: customerId })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Customer made inactive successfully.`);
            closeModal('deleteCustomerModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error making customer inactive: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitUpdateVendor(event) {
    event.preventDefault();
    
    const vendorData = {
        vendorId: document.getElementById('updateVendorId').value,
        displayName: document.getElementById('updateVendorDisplayName').value,
        companyName: document.getElementById('updateVendorCompanyName').value,
        givenName: document.getElementById('updateVendorGivenName').value,
        familyName: document.getElementById('updateVendorFamilyName').value,
        email: document.getElementById('updateVendorEmail').value,
        phone: document.getElementById('updateVendorPhone').value
    };
    
    fetch('/api/quickbooks/vendors/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(vendorData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Vendor "${data.displayName}" updated successfully.`);
            closeModal('updateVendorModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating vendor: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitDeleteVendor(event) {
    event.preventDefault();
    
    const vendorId = document.getElementById('deleteVendorId').value;
    
    if (!confirm('Are you sure you want to make this vendor inactive?')) {
        return;
    }
    
    fetch('/api/quickbooks/vendors/delete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ vendorId: vendorId })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Vendor made inactive successfully.`);
            closeModal('deleteVendorModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error making vendor inactive: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function loadCustomersForAction(operation) {
    fetch('/api/quickbooks/customers', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.customers) {
            if (operation === 'update') {
                const select = document.getElementById('updateCustomerId');
                if (select) {
                    select.innerHTML = '<option value="">Select a customer...</option>';
                    data.customers.forEach(customer => {
                        const option = document.createElement('option');
                        option.value = customer.id;
                        option.textContent = customer.displayName;
                        select.appendChild(option);
                    });
                }
            } else if (operation === 'delete') {
                const select = document.getElementById('deleteCustomerId');
                if (select) {
                    select.innerHTML = '<option value="">Select a customer...</option>';
                    data.customers.forEach(customer => {
                        const option = document.createElement('option');
                        option.value = customer.id;
                        option.textContent = customer.displayName;
                        select.appendChild(option);
                    });
                }
            }
        }
    })
    .catch(error => {
        console.error('Error loading customers:', error);
    });
}

function loadVendorsForAction(operation) {
    fetch('/api/quickbooks/vendors', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.vendors) {
            if (operation === 'update') {
                const select = document.getElementById('updateVendorId');
                if (select) {
                    select.innerHTML = '<option value="">Select a vendor...</option>';
                    data.vendors.forEach(vendor => {
                        const option = document.createElement('option');
                        option.value = vendor.id;
                        option.textContent = vendor.displayName;
                        select.appendChild(option);
                    });
                }
            } else if (operation === 'delete') {
                const select = document.getElementById('deleteVendorId');
                if (select) {
                    select.innerHTML = '<option value="">Select a vendor...</option>';
                    data.vendors.forEach(vendor => {
                        const option = document.createElement('option');
                        option.value = vendor.id;
                        option.textContent = vendor.displayName;
                        select.appendChild(option);
                    });
                }
            }
        }
    })
    .catch(error => {
        console.error('Error loading vendors:', error);
    });
}

function onCustomerSelectForUpdate() {
    const customerId = document.getElementById('updateCustomerId').value;
    if (customerId) {
        fetch('/api/quickbooks/customers', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => response.json())
        .then(data => {
            if (data.success && data.customers) {
                const customer = data.customers.find(c => c.id === customerId);
                if (customer) {
                    document.getElementById('updateCustomerDisplayName').value = customer.displayName || '';
                }
            }
        });
    }
}

function onVendorSelectForUpdate() {
    const vendorId = document.getElementById('updateVendorId').value;
    if (vendorId) {
        fetch('/api/quickbooks/vendors', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => response.json())
        .then(data => {
            if (data.success && data.vendors) {
                const vendor = data.vendors.find(v => v.id === vendorId);
                if (vendor) {
                    document.getElementById('updateVendorDisplayName').value = vendor.displayName || '';
                }
            }
        });
    }
}

function submitUpdateInvoice(event) {
    event.preventDefault();
    
    const qty = parseFloat(document.getElementById('updateInvoiceQuantity').value);
    const rate = parseFloat(document.getElementById('updateInvoiceRate').value);
    
    const invoiceData = {
        invoiceId: document.getElementById('updateInvoiceId').value,
        description: document.getElementById('updateInvoiceDescription').value,
        quantity: qty,
        rate: rate,
        amount: qty * rate
    };
    
    fetch('/api/quickbooks/invoices/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(invoiceData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Invoice #${data.docNumber} updated successfully.`);
            closeModal('updateInvoiceModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating invoice: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitVoidInvoice(event) {
    event.preventDefault();
    
    const invoiceId = document.getElementById('voidInvoiceId').value;
    
    if (!confirm('Are you sure you want to void this invoice?')) {
        return;
    }
    
    fetch('/api/quickbooks/invoices/void', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ invoiceId: invoiceId })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Invoice voided successfully.`);
            closeModal('voidInvoiceModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error voiding invoice: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitEmailInvoice(event) {
    event.preventDefault();
    
    const invoiceData = {
        invoiceId: document.getElementById('emailInvoiceId').value,
        emailTo: document.getElementById('emailInvoiceTo').value
    };
    
    fetch('/api/quickbooks/invoices/email', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(invoiceData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Invoice emailed successfully.`);
            closeModal('emailInvoiceModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error emailing invoice: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function loadInvoicesForAction() {
    fetch('/api/quickbooks/invoices', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.invoices) {
            const updateSelect = document.getElementById('updateInvoiceId');
            const voidSelect = document.getElementById('voidInvoiceId');
            const emailSelect = document.getElementById('emailInvoiceId');
            
            if (updateSelect) {
                updateSelect.innerHTML = '<option value="">Select an invoice...</option>';
                data.invoices.forEach(invoice => {
                    const option = document.createElement('option');
                    option.value = invoice.id;
                    const customerInfo = invoice.customerName ? ` (${invoice.customerName})` : '';
                    option.textContent = `Invoice #${invoice.docNumber} - $${invoice.totalAmt}${customerInfo}`;
                    updateSelect.appendChild(option);
                });
            }
            
            if (voidSelect) {
                voidSelect.innerHTML = '<option value="">Select an invoice...</option>';
                data.invoices.forEach(invoice => {
                    const option = document.createElement('option');
                    option.value = invoice.id;
                    const customerInfo = invoice.customerName ? ` (${invoice.customerName})` : '';
                    option.textContent = `Invoice #${invoice.docNumber} - $${invoice.totalAmt}${customerInfo}`;
                    voidSelect.appendChild(option);
                });
            }
            
            if (emailSelect) {
                emailSelect.innerHTML = '<option value="">Select an invoice...</option>';
                data.invoices.forEach(invoice => {
                    const option = document.createElement('option');
                    option.value = invoice.id;
                    const customerInfo = invoice.customerName ? ` (${invoice.customerName})` : '';
                    option.textContent = `Invoice #${invoice.docNumber} - $${invoice.totalAmt}${customerInfo}`;
                    emailSelect.appendChild(option);
                });
            }
        }
    })
    .catch(error => {
        console.error('Error loading invoices:', error);
    });
}

function updateInvoiceAmount() {
    const qty = parseFloat(document.getElementById('updateInvoiceQuantity').value) || 0;
    const rate = parseFloat(document.getElementById('updateInvoiceRate').value) || 0;
    const amount = qty * rate;
    document.getElementById('updateInvoiceAmount').textContent = '$' + amount.toFixed(2);
}

function submitVendor(event) {
    event.preventDefault();
    
    const vendorData = {
        displayName: document.getElementById('vendorDisplayName').value,
        companyName: document.getElementById('vendorCompanyName').value,
        givenName: document.getElementById('vendorGivenName').value,
        familyName: document.getElementById('vendorFamilyName').value,
        email: document.getElementById('vendorEmail').value,
        phone: document.getElementById('vendorPhone').value,
        street: document.getElementById('vendorStreet').value,
        city: document.getElementById('vendorCity').value,
        state: document.getElementById('vendorState').value,
        zip: document.getElementById('vendorZip').value
    };
    
    fetch('/api/quickbooks/vendors', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(vendorData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Vendor "${data.displayName}" created successfully.`);
            closeModal('vendorModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating vendor: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitInvoice(event) {
    event.preventDefault();
    
    const qty = parseFloat(document.getElementById('invoiceQuantity').value);
    const rate = parseFloat(document.getElementById('invoiceRate').value);
    
    const invoiceData = {
        customerId: document.getElementById('invoiceCustomer').value,
        description: document.getElementById('invoiceDescription').value,
        quantity: qty,
        rate: rate,
        amount: qty * rate
    };
    
    fetch('/api/quickbooks/invoices', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(invoiceData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Invoice #${data.docNumber} created successfully.`);
            closeModal('invoiceModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating invoice: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitPayment(event) {
    event.preventDefault();
    
    const paymentData = {
        customerId: document.getElementById('paymentCustomer').value,
        invoiceId: document.getElementById('paymentInvoice').value,
        amount: parseFloat(document.getElementById('paymentAmount').value)
    };
    
    fetch('/api/quickbooks/payments', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(paymentData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Payment of $${data.totalAmt} recorded successfully.`);
            closeModal('paymentModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating payment: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitUpdatePayment(event) {
    event.preventDefault();
    
    const paymentData = {
        paymentId: document.getElementById('updatePaymentId').value,
        amount: parseFloat(document.getElementById('updatePaymentAmount').value)
    };
    
    fetch('/api/quickbooks/payments/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(paymentData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Payment updated successfully.`);
            closeModal('updatePaymentModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating payment: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitVoidPayment(event) {
    event.preventDefault();
    
    const paymentId = document.getElementById('voidPaymentId').value;
    
    if (!confirm('Are you sure you want to void this payment?')) {
        return;
    }
    
    fetch('/api/quickbooks/payments/void', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ paymentId: paymentId })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Payment voided successfully.`);
            closeModal('voidPaymentModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error voiding payment: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function submitDeletePayment(event) {
    event.preventDefault();
    
    const paymentId = document.getElementById('deletePaymentId').value;
    
    if (!confirm('Are you sure you want to delete this payment?')) {
        return;
    }
    
    fetch('/api/quickbooks/payments/delete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ paymentId: paymentId })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Payment deleted successfully.`);
            closeModal('deletePaymentModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error deleting payment: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error: ' + error.message, true);
    });
}

function loadPaymentsForAction() {
    fetch('/api/quickbooks/payments', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.payments) {
            const updateSelect = document.getElementById('updatePaymentId');
            const voidSelect = document.getElementById('voidPaymentId');
            const deleteSelect = document.getElementById('deletePaymentId');
            
            if (updateSelect) {
                updateSelect.innerHTML = '<option value="">Select a payment...</option>';
                data.payments.forEach(payment => {
                    const option = document.createElement('option');
                    option.value = payment.id;
                    const customerInfo = payment.customerName ? ` (${payment.customerName})` : '';
                    option.textContent = `Payment #${payment.id} - $${payment.totalAmt}${customerInfo}`;
                    updateSelect.appendChild(option);
                });
            }
            
            if (voidSelect) {
                voidSelect.innerHTML = '<option value="">Select a payment...</option>';
                data.payments.forEach(payment => {
                    const option = document.createElement('option');
                    option.value = payment.id;
                    const customerInfo = payment.customerName ? ` (${payment.customerName})` : '';
                    option.textContent = `Payment #${payment.id} - $${payment.totalAmt}${customerInfo}`;
                    voidSelect.appendChild(option);
                });
            }
            
            if (deleteSelect) {
                deleteSelect.innerHTML = '<option value="">Select a payment...</option>';
                data.payments.forEach(payment => {
                    const option = document.createElement('option');
                    option.value = payment.id;
                    const customerInfo = payment.customerName ? ` (${payment.customerName})` : '';
                    option.textContent = `Payment #${payment.id} - $${payment.totalAmt}${customerInfo}`;
                    deleteSelect.appendChild(option);
                });
            }
        }
    })
    .catch(error => {
        console.error('Error loading payments:', error);
    });
}

// ===========================
// BILL OPERATIONS
// ===========================

function submitBill(event) {
    event.preventDefault();
    
    const billData = {
        vendorId: document.getElementById('billVendorId').value,
        amount: document.getElementById('billAmount').value,
        expenseAccountId: document.getElementById('billExpenseAccountId').value,
        description: document.getElementById('billDescription').value || 'Bill expense'
    };
    
    fetch('/api/quickbooks/bills', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(billData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Bill #${data.docNumber} created successfully.`);
            closeModal('billModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating bill: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error creating bill: ' + error.message, true);
    });
}

function submitUpdateBill(event) {
    event.preventDefault();
    
    const billId = document.getElementById('updateBillId').value;
    const billData = {
        amount: document.getElementById('updateBillAmount').value,
        description: document.getElementById('updateBillDescription').value || 'Updated bill'
    };
    
    fetch(`/api/quickbooks/bills/${billId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(billData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus(`Bill #${data.docNumber} updated successfully.`);
            closeModal('updateBillModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating bill: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error updating bill: ' + error.message, true);
    });
}

function submitDeleteBill(event) {
    event.preventDefault();
    
    const billId = document.getElementById('deleteBillId').value;
    
    if (!confirm('Are you sure you want to delete this bill?')) {
        return;
    }
    
    fetch(`/api/quickbooks/bills/${billId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus('Bill deleted successfully.');
            closeModal('deleteBillModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error deleting bill: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error deleting bill: ' + error.message, true);
    });
}

function loadVendors() {
    console.log('Loading vendors for bill creation...');
    fetch('/api/quickbooks/vendors')
    .then(response => response.json())
    .then(data => {
        console.log('Vendors response:', data);
        if (data.success && data.vendors) {
            const vendorSelect = document.getElementById('billVendorId');
            if (vendorSelect) {
                vendorSelect.innerHTML = '<option value="">Select Vendor</option>';
                data.vendors.forEach(vendor => {
                    const option = document.createElement('option');
                    option.value = vendor.id;
                    option.textContent = vendor.displayName;
                    vendorSelect.appendChild(option);
                });
                console.log(`Loaded ${data.vendors.length} vendors`);
            } else {
                console.error('billVendorId select element not found');
            }
        } else {
            console.error('No vendors in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading vendors:', error);
    });
}

function loadExpenseAccounts() {
    console.log('Loading expense accounts for bill creation...');
    fetch('/api/quickbooks/accounts/expense')
    .then(response => response.json())
    .then(data => {
        console.log('Expense accounts response:', data);
        if (data.success && data.accounts) {
            const accountSelect = document.getElementById('billExpenseAccountId');
            if (accountSelect) {
                accountSelect.innerHTML = '<option value="">Select Expense Account</option>';
                data.accounts.forEach(account => {
                    const option = document.createElement('option');
                    option.value = account.id;
                    // Show fully qualified name for clarity (e.g., "Office Expenses:Supplies")
                    option.textContent = account.fullyQualifiedName || account.name;
                    accountSelect.appendChild(option);
                });
                console.log(`Loaded ${data.accounts.length} expense accounts`);
            } else {
                console.error('billExpenseAccountId select element not found');
            }
        } else {
            console.error('No expense accounts in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading expense accounts:', error);
    });
}

function loadBillsForAction() {
    console.log('Loading bills for update/delete...');
    fetch('/api/quickbooks/bills')
    .then(response => response.json())
    .then(data => {
        console.log('Bills response:', data);
        if (data.success && data.bills) {
            const updateSelect = document.getElementById('updateBillId');
            const deleteSelect = document.getElementById('deleteBillId');
            
            if (updateSelect) {
                updateSelect.innerHTML = '<option value="">Select a bill...</option>';
                data.bills.forEach(bill => {
                    const option = document.createElement('option');
                    option.value = bill.id;
                    const vendorInfo = bill.vendorName ? ` (${bill.vendorName})` : '';
                    option.textContent = `Bill #${bill.docNumber}${vendorInfo} - Balance: $${bill.balance || '0.00'}`;
                    updateSelect.appendChild(option);
                });
                console.log(`Loaded ${data.bills.length} bills for update`);
            }
            
            if (deleteSelect) {
                deleteSelect.innerHTML = '<option value="">Select a bill...</option>';
                data.bills.forEach(bill => {
                    const option = document.createElement('option');
                    option.value = bill.id;
                    const vendorInfo = bill.vendorName ? ` (${bill.vendorName})` : '';
                    option.textContent = `Bill #${bill.docNumber}${vendorInfo} - Balance: $${bill.balance || '0.00'}`;
                    deleteSelect.appendChild(option);
                });
                console.log(`Loaded ${data.bills.length} bills for delete`);
            }
        } else {
            console.error('No bills in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading bills:', error);
    });
}

function onBillSelectForUpdate() {
    // Placeholder for any logic when bill is selected
    console.log('Bill selected for update');
}

// ===========================
// JOURNAL ENTRY OPERATIONS
// ===========================

function submitJournalEntry(event) {
    event.preventDefault();
    
    const journalEntryData = {
        amount: document.getElementById('jeAmount').value,
        description: document.getElementById('jeDescription').value,
        debitAccountId: document.getElementById('jeDebitAccountId').value,
        creditAccountId: document.getElementById('jeCreditAccountId').value
    };
    
    fetch('/api/quickbooks/journalentries', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(journalEntryData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus('Journal Entry created successfully. Doc Number: ' + data.docNumber);
            closeModal('journalEntryModal');
            document.getElementById('journalEntryForm').reset();
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating journal entry: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error creating journal entry: ' + error.message, true);
    });
}

function submitUpdateJournalEntry(event) {
    event.preventDefault();
    
    const journalEntryId = document.getElementById('updateJournalEntryId').value;
    const journalEntryData = {
        amount: document.getElementById('updateJeAmount').value,
        description: document.getElementById('updateJeDescription').value
    };
    
    fetch(`/api/quickbooks/journalentries/${journalEntryId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(journalEntryData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus('Journal Entry updated successfully.');
            closeModal('updateJournalEntryModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating journal entry: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error updating journal entry: ' + error.message, true);
    });
}

function submitDeleteJournalEntry(event) {
    event.preventDefault();
    
    const journalEntryId = document.getElementById('deleteJournalEntryId').value;
    
    if (!confirm('Are you sure you want to delete this journal entry?')) {
        return;
    }
    
    fetch(`/api/quickbooks/journalentries/${journalEntryId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus('Journal Entry deleted successfully.');
            closeModal('deleteJournalEntryModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error deleting journal entry: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error deleting journal entry: ' + error.message, true);
    });
}

function loadAccountsForJournalEntry() {
    console.log('Loading accounts for journal entry...');
    fetch('/api/quickbooks/accounts/expense')
    .then(response => response.json())
    .then(data => {
        console.log('Accounts response:', data);
        if (data.success && data.accounts) {
            const debitSelect = document.getElementById('jeDebitAccountId');
            const creditSelect = document.getElementById('jeCreditAccountId');
            
            if (debitSelect) {
                debitSelect.innerHTML = '<option value="">Select debit account...</option>';
                data.accounts.forEach(account => {
                    const option = document.createElement('option');
                    option.value = account.id;
                    option.textContent = account.fullyQualifiedName || account.name;
                    debitSelect.appendChild(option);
                });
                console.log(`Loaded ${data.accounts.length} accounts for debit`);
            }
            
            if (creditSelect) {
                creditSelect.innerHTML = '<option value="">Select credit account...</option>';
                data.accounts.forEach(account => {
                    const option = document.createElement('option');
                    option.value = account.id;
                    option.textContent = account.fullyQualifiedName || account.name;
                    creditSelect.appendChild(option);
                });
                console.log(`Loaded ${data.accounts.length} accounts for credit`);
            }
        } else {
            console.error('No accounts in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading accounts:', error);
    });
}

function loadJournalEntriesForAction() {
    console.log('Loading journal entries for update/delete...');
    fetch('/api/quickbooks/journalentries')
    .then(response => response.json())
    .then(data => {
        console.log('Journal entries response:', data);
        if (data.success && data.journalEntries) {
            const updateSelect = document.getElementById('updateJournalEntryId');
            const deleteSelect = document.getElementById('deleteJournalEntryId');
            
            if (updateSelect) {
                updateSelect.innerHTML = '<option value="">Select a journal entry...</option>';
                data.journalEntries.forEach(je => {
                    const option = document.createElement('option');
                    option.value = je.id;
                    option.textContent = `JE #${je.docNumber} - Amount: $${je.amount || '0.00'}`;
                    updateSelect.appendChild(option);
                });
                console.log(`Loaded ${data.journalEntries.length} journal entries for update`);
            }
            
            if (deleteSelect) {
                deleteSelect.innerHTML = '<option value="">Select a journal entry...</option>';
                data.journalEntries.forEach(je => {
                    const option = document.createElement('option');
                    option.value = je.id;
                    option.textContent = `JE #${je.docNumber} - Amount: $${je.amount || '0.00'}`;
                    deleteSelect.appendChild(option);
                });
                console.log(`Loaded ${data.journalEntries.length} journal entries for delete`);
            }
        } else {
            console.error('No journal entries in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading journal entries:', error);
    });
}

function onJournalEntrySelectForUpdate() {
    // Placeholder for any logic when journal entry is selected
    console.log('Journal entry selected for update');
}

// ===========================
// PURCHASE (EXPENSE) OPERATIONS
// ===========================

function submitPurchase(event) {
    event.preventDefault();
    
    const vendorValue = document.getElementById('purchaseVendorId').value;
    const purchaseData = {
        amount: document.getElementById('purchaseAmount').value,
        description: document.getElementById('purchaseDescription').value,
        accountId: document.getElementById('purchaseAccountId').value,
        vendorId: vendorValue && vendorValue !== '' ? vendorValue : null
    };
    
    fetch('/api/quickbooks/purchases', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(purchaseData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            const identifier = data.docNumber ? `Doc Number: ${data.docNumber}` : `ID: ${data.id}`;
            showStatus(`Purchase created successfully. ${identifier}`);
            closeModal('purchaseModal');
            document.getElementById('purchaseForm').reset();
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error creating purchase: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error creating purchase: ' + error.message, true);
    });
}

function submitUpdatePurchase(event) {
    event.preventDefault();
    
    const purchaseId = document.getElementById('updatePurchaseId').value;
    const purchaseData = {
        amount: document.getElementById('updatePurchaseAmount').value,
        description: document.getElementById('updatePurchaseDescription').value
    };
    
    fetch(`/api/quickbooks/purchases/${purchaseId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(purchaseData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus('Purchase updated successfully.');
            closeModal('updatePurchaseModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error updating purchase: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error updating purchase: ' + error.message, true);
    });
}

function submitDeletePurchase(event) {
    event.preventDefault();
    
    const purchaseId = document.getElementById('deletePurchaseId').value;
    
    if (!confirm('Are you sure you want to delete this purchase?')) {
        return;
    }
    
    fetch(`/api/quickbooks/purchases/${purchaseId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showStatus('Purchase deleted successfully.');
            closeModal('deletePurchaseModal');
            setTimeout(() => location.reload(), 1000);
        } else {
            showStatus('Error deleting purchase: ' + data.error, true);
        }
    })
    .catch(error => {
        showStatus('Error deleting purchase: ' + error.message, true);
    });
}

function loadAccountsForPurchase() {
    console.log('Loading accounts for purchase...');
    fetch('/api/quickbooks/accounts/purchase')
    .then(response => response.json())
    .then(data => {
        console.log('Accounts response:', data);
        if (data.success && data.accounts) {
            const accountSelect = document.getElementById('purchaseAccountId');
            
            if (accountSelect) {
                accountSelect.innerHTML = '<option value="">Select expense account...</option>';
                data.accounts.forEach(account => {
                    const option = document.createElement('option');
                    option.value = account.id;
                    const accountType = account.accountType ? ` (${account.accountType})` : '';
                    option.textContent = `${account.fullyQualifiedName || account.name}${accountType}`;
                    accountSelect.appendChild(option);
                });
                console.log(`Loaded ${data.accounts.length} valid accounts for purchase`);
            }
        } else {
            console.error('No accounts in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading accounts:', error);
    });
}

function loadVendorsForPurchase() {
    console.log('Loading vendors for purchase...');
    fetch('/api/quickbooks/vendors')
    .then(response => response.json())
    .then(data => {
        console.log('Vendors response:', data);
        if (data.success && data.vendors) {
            const vendorSelect = document.getElementById('purchaseVendorId');
            
            if (vendorSelect) {
                vendorSelect.innerHTML = '<option value="">Select vendor (optional)...</option>';
                data.vendors.forEach(vendor => {
                    const option = document.createElement('option');
                    option.value = vendor.id;
                    option.textContent = vendor.displayName;
                    vendorSelect.appendChild(option);
                });
                console.log(`Loaded ${data.vendors.length} vendors for purchase`);
            }
        } else {
            console.error('No vendors in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading vendors:', error);
    });
}

function loadPurchasesForAction() {
    console.log('Loading purchases for update/delete...');
    fetch('/api/quickbooks/purchases')
    .then(response => response.json())
    .then(data => {
        console.log('Purchases response:', data);
        if (data.success && data.purchases) {
            const updateSelect = document.getElementById('updatePurchaseId');
            const deleteSelect = document.getElementById('deletePurchaseId');
            
            if (updateSelect) {
                updateSelect.innerHTML = '<option value="">Select a purchase...</option>';
                data.purchases.forEach(purchase => {
                    const option = document.createElement('option');
                    option.value = purchase.id;
                    const displayName = purchase.docNumber ? `Purchase #${purchase.docNumber}` : `Purchase ID: ${purchase.id}`;
                    option.textContent = `${displayName} - Amount: $${purchase.amount || '0.00'}`;
                    updateSelect.appendChild(option);
                });
                console.log(`Loaded ${data.purchases.length} purchases for update`);
            }
            
            if (deleteSelect) {
                deleteSelect.innerHTML = '<option value="">Select a purchase...</option>';
                data.purchases.forEach(purchase => {
                    const option = document.createElement('option');
                    option.value = purchase.id;
                    const displayName = purchase.docNumber ? `Purchase #${purchase.docNumber}` : `Purchase ID: ${purchase.id}`;
                    option.textContent = `${displayName} - Amount: $${purchase.amount || '0.00'}`;
                    deleteSelect.appendChild(option);
                });
                console.log(`Loaded ${data.purchases.length} purchases for delete`);
            }
        } else {
            console.error('No purchases in response or success=false');
        }
    })
    .catch(error => {
        console.error('Error loading purchases:', error);
    });
}

function onPurchaseSelectForUpdate() {
    // Placeholder for any logic when purchase is selected
    console.log('Purchase selected for update');
}
