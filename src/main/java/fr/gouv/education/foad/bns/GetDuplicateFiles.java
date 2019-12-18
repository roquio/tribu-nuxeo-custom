package fr.gouv.education.foad.bns;

import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.query.NxqlQueryConverter;
import org.nuxeo.runtime.api.Framework;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Operation(
        id = GetDuplicateFiles.ID,
        category = Constants.CAT_DOCUMENT,
        label = "Get duplicated documents by path",
        description = "Get documents which share the same path, perform en ES query and aggregate by count on ecm:path")
public class GetDuplicateFiles {

    public static final String ID = "Document.GetDuplicateFiles";
    
    @Context
    protected CoreSession session;
    
	@OperationMethod
	public Object run(DocumentRef docRef) throws ClientException {
		
        JSONObject duplicatedItems = new JSONObject();
        
        ElasticSearchAdmin esAdmin = Framework.getService(ElasticSearchAdmin.class);
        
        SearchRequestBuilder request = esAdmin.getClient()
				.prepareSearch(esAdmin.getIndexNameForRepository(session.getRepositoryName()))
				.setTypes("doc").setSearchType(SearchType.QUERY_THEN_FETCH);
        
        String clause = " ecm:path startswith " ;
        
		String NXQLClause = "select ecm:uuid from Document where ((%s '%s') AND ecm:isVersion = 0)";
		
		QueryBuilder queryBuilder = NxqlQueryConverter.toESQueryBuilder(String.format(NXQLClause, clause, docRef.toString()), session);

		request.setQuery(queryBuilder);
		
		// Sum aggregation
		TermsBuilder aggregation = AggregationBuilders.terms("top_ecm:path").field("ecm:path").size(1000)
				.order(Terms.Order.aggregation("_count", false));;
		
		request.addAggregation(aggregation);

		SearchResponse response = request.get();

	    Map<String, Aggregation> results = response.getAggregations().asMap();
	    StringTerms topField = (StringTerms) results.get("top_ecm:path");
	    
    	//Map<String, Long> map = new HashMap<String, Long>();

	    JSONArray docs = new JSONArray();
	    
	    for(Terms.Bucket b : topField.getBuckets()) {

	    	if(b.getDocCount() > 1) {
		    	JSONObject doc = new JSONObject();		    	
		    	doc.put("path", b.getKey());
		    	doc.put("count", b.getDocCount());
			    docs.add(doc);
	    	}
	    }
	    
	    
    	duplicatedItems.put("docs", docs);
	
        return new StringBlob(duplicatedItems.toString(), "application/json");
	}
    

}
