package cz.inovatika.sdnnt.services.impl.rules;

import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

import cz.inovatika.sdnnt.services.impl.granularities.GranularityField;
import cz.inovatika.sdnnt.services.impl.utils.SKCYearsUtils;
import cz.inovatika.sdnnt.services.impl.utils.SolrYearsUtils;


// 911      |a ZRE802 |d zdigitalizováno |r 1928,1930-31 |s 10, 13-14, 16-18 |u http://kramerius.kkvysociny.cz/search/handle/uuid:5cd4d04b-3417-4a8a-9361-bbbf980d6216
// 
// argumentace: 
//  oai:aleph-nkp.cz:SKC01-007434937 vs oai:aleph-nkp.cz:SKC01-000084799
// oai:aleph-nkp.cz:SKC01-005163910 vs oai:aleph-nkp.cz:SKC01-005163907 
public class Marc911Rule {
    // range
    private String range;
    private String url;
    private String dlAcronym;
    
    public Marc911Rule(String range, String url, String dlAcronym) {
        super();
        this.range = range;
        this.url = url;
        this.dlAcronym = dlAcronym;
    }

    public String getRange() {
        return range;
    }
    
    public String getUrl() {
        return url;
    }
    
    public String getDlAcronym() {
        return dlAcronym;
    }

    public String getPid() {
        String u = this.url.trim();
        int indexOf = u.indexOf("uuid:");
        return u.substring(indexOf);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(dlAcronym, range, url);
    }

    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Marc911Rule other = (Marc911Rule) obj;
        return Objects.equals(dlAcronym, other.dlAcronym) && Objects.equals(range, other.range)
                && Objects.equals(url, other.url);
    }

    public boolean acceptField(GranularityField f) {
        List<Pair<Integer, Integer>> rules = SKCYearsUtils.skcRange(this.range);
        String date = f.getDate();
        if (date != null) {
            Integer date2 = SolrYearsUtils.solrDate(date);
            for (Pair<Integer, Integer> p : rules) {
                if (date2 >= p.getLeft() && date2 <= p.getRight()) {
                    return true;
                }
            }
            return false;
        } else
            return true;
    }    
    
    

    
}
