package cz.inovatika.sdnnt.model.workflow.document;

import cz.inovatika.sdnnt.index.utils.HistoryObjectUtils;
import cz.inovatika.sdnnt.indexer.models.MarcRecord;
import cz.inovatika.sdnnt.model.CuratorItemState;
import cz.inovatika.sdnnt.model.DataCollections;
import cz.inovatika.sdnnt.model.PublicItemState;
import cz.inovatika.sdnnt.model.TransitionType;
import cz.inovatika.sdnnt.model.Period;
import cz.inovatika.sdnnt.model.workflow.SwitchStateOptions;
import cz.inovatika.sdnnt.model.workflow.WorkflowOwner;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Reprezentuje dokument 
 * @author happy
 */
public class DocumentProxy implements WorkflowOwner {

    protected MarcRecord marcRecord;

    public DocumentProxy(MarcRecord record) {
        this.marcRecord = record;
    }

    @Override
    public CuratorItemState getWorkflowState() {
        return !this.marcRecord.kuratorstav.isEmpty() ? CuratorItemState.valueOf(this.marcRecord.kuratorstav.get(0)) : null;
    }

    @Override
    public PublicItemState getPublicState() {
        return !this.marcRecord.dntstav.isEmpty() ? PublicItemState.valueOf(this.marcRecord.dntstav.get(0)) : null;
    }

    @Override
    public Date getPublicStateDate() {
        return this.marcRecord.datum_krator_stavu;
    }

    @Override
    public void switchWorkflowState(SwitchStateOptions options, CuratorItemState itm, String license, boolean changingLicenseState, Period period, String originator, String user, String poznamka) {
        Date date = new Date();

        List<String> dntstav = this.marcRecord.dntstav;
        List<String> kuratorStav = this.marcRecord.kuratorstav;

        PublicItemState publicItemState = itm.getPublicItemState(this);

        if (publicItemState != null && (!dntstav.contains(publicItemState.name()) || changingLicenseState)) {
            this.marcRecord.dntstav = new ArrayList<>(Arrays.asList(itm.getPublicItemState(this).name()));
            this.marcRecord.datum_stavu = new Date(date.getTime());
            this.marcRecord.previousDntstav = dntstav;

            JSONObject historyObject = new JSONObject();
            historyObject.put("stav", publicItemState);
            historyObject.put("date", MarcRecord.FORMAT.format(this.marcRecord.datum_stavu));
            if (user != null) {
                historyObject.put("user", user);
            }
            if (poznamka != null) {
                historyObject.put("comment", poznamka);
            }
            if (license != null) {
                historyObject.put("license", license);
            }

            if (originator != null) {
                historyObject.put("zadost", originator);
            }

            this.marcRecord.historie_stavu.put(historyObject);
        }
        this.marcRecord.kuratorstav = new ArrayList<>(Arrays.asList(itm.name()));
        if (kuratorStav != null) this.marcRecord.previousKuratorstav = kuratorStav;
        this.marcRecord.datum_krator_stavu = new Date(date.getTime());

        JSONObject historyObject = HistoryObjectUtils.historyObjectParent(itm.name(), license, originator, user, poznamka, MarcRecord.FORMAT.format(this.marcRecord.datum_krator_stavu));
        this.marcRecord.historie_kurator_stavu.put(historyObject);

        if (changingLicenseState) {
            if (this.marcRecord.licenseHistory == null) {
                this.marcRecord.licenseHistory = new ArrayList<>();
            }
            if (marcRecord.license != null) this.marcRecord.licenseHistory.add(marcRecord.license);
            this.marcRecord.license = license;
        }
    }

    @Override
    public boolean isSwitchToNextStatePossible(Date date, Period period) {
        if (period.getTransitionType().equals(TransitionType.kurator)) {
            return true;
        } else {
            //Date deadlineDate = period.defineDeadline(getWorkflowDate());
            Date deadlineDate = period.defineDeadline(date);
            return new Date().after(deadlineDate);
        }
    }

    @Override
    public Date getWorkflowDate() {
        return this.marcRecord.datum_krator_stavu;
    }

    @Override
    public void setWorkflowDate(Date date) {
        this.marcRecord.datum_krator_stavu = date;
        this.marcRecord.datum_stavu = date;
    }

    @Override
    public void setPeriodBetweenStates(Period period) {
        // nepotrebuju nastavit, vypocitava se dynamicky
    }

    @Override
    public String getLicense() {
        return marcRecord.license;
    }

    @Override
    public void setLicense(String l) {
        this.marcRecord.license = l;
    }

    @Override
    public List<Pair<String, SolrInputDocument>> getStateToSave(SwitchStateOptions options) {
        Pair<String, SolrInputDocument> pair = Pair.of(DataCollections.catalog.name(), marcRecord.toSolrDoc());
        return Arrays.asList(pair);
    }

    @Override
    public boolean hasRejectableWorkload() {
        return false;
    }

    @Override
    public void rejectWorkflowState(String originator, String user, String poznamka) {
    }
    
    
}
