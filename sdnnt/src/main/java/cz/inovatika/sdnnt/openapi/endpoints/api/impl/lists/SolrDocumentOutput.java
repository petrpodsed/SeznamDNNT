package cz.inovatika.sdnnt.openapi.endpoints.api.impl.lists;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface SolrDocumentOutput {

    public static final String PID_KEY = "pid";
    public static final String SELECTED_INSTITUTION_KEY = "institution";

    public static final String LABEL_KEY = "label";
    public static final String NAZEV_KEY = "title";
    public static final String IDENTIFIER_KEY = "identifier";
    public static final String PIDS_KEY = "pids";
    public static final String GRANUARITY_KEY = "granularity";


    //void output(String selectedInstitution, String label, Collection<Object> nazev, String identifier, String... pids);
    void output(Map<String, Object> outputDocument, List<String> fields, String endpointLicense);

}
