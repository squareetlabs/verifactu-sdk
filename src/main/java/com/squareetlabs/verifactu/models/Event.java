package com.squareetlabs.verifactu.models;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An entry of the event log of a billing system ("registro de evento"). Events are generated, chained and signed
 * locally: they are never sent to the AEAT.
 * <p>
 * Each event of an obligor is chained to the previous one: pass it with {@link #setPrevious(Reference)}, or leave it
 * {@code null} for the first event ("PrimerEvento").
 */
public class Event {

    private final EventType type;
    private final String obligorTaxId;
    private final String obligorName;
    private ZonedDateTime generatedAt = ZonedDateTime.now(ZoneId.of("Europe/Madrid"));
    private Reference previous;
    private String otherData;
    private Object details;

    public Event(EventType type, String obligorTaxId, String obligorName) {
        this.type = type;
        this.obligorTaxId = obligorTaxId;
        this.obligorName = obligorName;
    }

    public EventType getType() {
        return type;
    }

    public String getObligorTaxId() {
        return obligorTaxId;
    }

    public String getObligorName() {
        return obligorName;
    }

    public ZonedDateTime getGeneratedAt() {
        return generatedAt;
    }

    public Event setGeneratedAt(ZonedDateTime generatedAt) {
        this.generatedAt = generatedAt;
        return this;
    }

    public Reference getPrevious() {
        return previous;
    }

    /** The previous event of the chain, or {@code null} for the first event. */
    public Event setPrevious(Reference previous) {
        this.previous = previous;
        return this;
    }

    public String getOtherData() {
        return otherData;
    }

    /** Free text of up to 100 characters ({@code OtrosDatosEvento}). */
    public Event setOtherData(String otherData) {
        this.otherData = otherData;
        return this;
    }

    public Object getDetails() {
        return details;
    }

    /**
     * The data specific to the type: {@link ProcessLaunch} (types 03 and 05), {@link AnomalyDetection} (04 and 06),
     * {@link BillingExport} (08), {@link EventExport} (09) or {@link Summary} (10). The other types carry none.
     */
    public Event setDetails(Object details) {
        this.details = details;
        return this;
    }

    /** A reference to an event: its type, generation date and hash. */
    public static class Reference {
        private final EventType type;
        private final ZonedDateTime generatedAt;
        private final String hash;

        public Reference(EventType type, ZonedDateTime generatedAt, String hash) {
            this.type = type;
            this.generatedAt = generatedAt;
            this.hash = hash;
        }

        public EventType getType() {
            return type;
        }

        public ZonedDateTime getGeneratedAt() {
            return generatedAt;
        }

        public String getHash() {
            return hash;
        }
    }

    /** A reference to a billing record: issuer, number and date, and optionally its hash. */
    public static class InvoiceReference {
        private final String issuerTaxId;
        private final String number;
        private final LocalDate date;
        private final String hash;

        public InvoiceReference(String issuerTaxId, String number, LocalDate date, String hash) {
            this.issuerTaxId = issuerTaxId;
            this.number = number;
            this.date = date;
            this.hash = hash;
        }

        public String getIssuerTaxId() {
            return issuerTaxId;
        }

        public String getNumber() {
            return number;
        }

        public LocalDate getDate() {
            return date;
        }

        public String getHash() {
            return hash;
        }
    }

    /** One of the four checks of an anomaly detection process: whether it ran and how many records it processed. */
    public static class Check {
        private final boolean performed;
        private final Integer processed;

        public Check(boolean performed, Integer processed) {
            this.performed = performed;
            this.processed = processed;
        }

        public boolean isPerformed() {
            return performed;
        }

        public Integer getProcessed() {
            return processed;
        }
    }

    /** Launch of the anomaly detection process on billing records (type 03) or on event records (type 05). */
    public static class ProcessLaunch {
        private final Check hashes;
        private final Check signatures;
        private final Check chain;
        private final Check dates;

        public ProcessLaunch(Check hashes, Check signatures, Check chain, Check dates) {
            this.hashes = hashes;
            this.signatures = signatures;
            this.chain = chain;
            this.dates = dates;
        }

        public Check getHashes() {
            return hashes;
        }

        public Check getSignatures() {
            return signatures;
        }

        public Check getChain() {
            return chain;
        }

        public Check getDates() {
            return dates;
        }
    }

    /** An anomaly detected in a billing record (type 04, with {@link #setInvoice}) or an event record (type 06, with {@link #setEvent}). */
    public static class AnomalyDetection {
        private final AnomalyType anomalyType;
        private String otherData;
        private InvoiceReference invoice;
        private Reference event;

        public AnomalyDetection(AnomalyType anomalyType) {
            this.anomalyType = anomalyType;
        }

        public AnomalyType getAnomalyType() {
            return anomalyType;
        }

        public String getOtherData() {
            return otherData;
        }

        /** Free text of up to 100 characters ({@code OtrosDatosAnomalia}). */
        public AnomalyDetection setOtherData(String otherData) {
            this.otherData = otherData;
            return this;
        }

        public InvoiceReference getInvoice() {
            return invoice;
        }

        public AnomalyDetection setInvoice(InvoiceReference invoice) {
            this.invoice = invoice;
            return this;
        }

        public Reference getEvent() {
            return event;
        }

        public AnomalyDetection setEvent(Reference event) {
            this.event = event;
            return this;
        }
    }

    /** The export of the billing records of a period (type 08). */
    public static class BillingExport {
        private final ZonedDateTime from;
        private final ZonedDateTime to;
        private final InvoiceReference first;
        private final InvoiceReference last;
        private final int registrations;
        private final String totalTax;
        private final String totalAmount;
        private final int annulments;
        private final boolean noLongerKept;

        public BillingExport(ZonedDateTime from, ZonedDateTime to, InvoiceReference first, InvoiceReference last,
                             int registrations, String totalTax, String totalAmount, int annulments,
                             boolean noLongerKept) {
            this.from = from;
            this.to = to;
            this.first = first;
            this.last = last;
            this.registrations = registrations;
            this.totalTax = totalTax;
            this.totalAmount = totalAmount;
            this.annulments = annulments;
            this.noLongerKept = noLongerKept;
        }

        public ZonedDateTime getFrom() {
            return from;
        }

        public ZonedDateTime getTo() {
            return to;
        }

        public InvoiceReference getFirst() {
            return first;
        }

        public InvoiceReference getLast() {
            return last;
        }

        public int getRegistrations() {
            return registrations;
        }

        public String getTotalTax() {
            return totalTax;
        }

        public String getTotalAmount() {
            return totalAmount;
        }

        public int getAnnulments() {
            return annulments;
        }

        public boolean isNoLongerKept() {
            return noLongerKept;
        }
    }

    /** The export of the event records of a period (type 09). */
    public static class EventExport {
        private final ZonedDateTime from;
        private final ZonedDateTime to;
        private final Reference first;
        private final Reference last;
        private final int events;
        private final boolean noLongerKept;

        public EventExport(ZonedDateTime from, ZonedDateTime to, Reference first, Reference last, int events,
                           boolean noLongerKept) {
            this.from = from;
            this.to = to;
            this.first = first;
            this.last = last;
            this.events = events;
            this.noLongerKept = noLongerKept;
        }

        public ZonedDateTime getFrom() {
            return from;
        }

        public ZonedDateTime getTo() {
            return to;
        }

        public Reference getFirst() {
            return first;
        }

        public Reference getLast() {
            return last;
        }

        public int getEvents() {
            return events;
        }

        public boolean isNoLongerKept() {
            return noLongerKept;
        }
    }

    /** The summary of the events since the previous summary (type 10). */
    public static class Summary {
        private final List<EventCount> counts = new ArrayList<>();
        private InvoiceReference first;
        private InvoiceReference last;
        private final int registrations;
        private final String totalTax;
        private final String totalAmount;
        private final int annulments;

        public Summary(int registrations, String totalTax, String totalAmount, int annulments) {
            this.registrations = registrations;
            this.totalTax = totalTax;
            this.totalAmount = totalAmount;
            this.annulments = annulments;
        }

        /** Adds how many events of a type were generated (up to 20 types). */
        public Summary count(EventType type, int number) {
            counts.add(new EventCount(type, number));
            return this;
        }

        public Summary setBillingRange(InvoiceReference first, InvoiceReference last) {
            this.first = first;
            this.last = last;
            return this;
        }

        public List<EventCount> getCounts() {
            return counts;
        }

        public InvoiceReference getFirst() {
            return first;
        }

        public InvoiceReference getLast() {
            return last;
        }

        public int getRegistrations() {
            return registrations;
        }

        public String getTotalTax() {
            return totalTax;
        }

        public String getTotalAmount() {
            return totalAmount;
        }

        public int getAnnulments() {
            return annulments;
        }
    }

    /** How many events of a type a summary counts. */
    public static class EventCount {
        private final EventType type;
        private final int number;

        public EventCount(EventType type, int number) {
            this.type = type;
            this.number = number;
        }

        public EventType getType() {
            return type;
        }

        public int getNumber() {
            return number;
        }
    }
}
