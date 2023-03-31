package fr.gouv.education.foad.data;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
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

import java.util.Map;

/**
 * @author Loïc Billon
 */
@Operation(
        id = GetDuplicateFilesByWebId.ID,
        category = Constants.CAT_DOCUMENT,
        label = "Get duplicated documents by webid",
        description = "Get documents which share the same webid, perform en ES query and aggregate by count on ttc;webid")
public class GetDuplicateFilesByWebId {

    public static final String ID = "Document.GetDuplicateFilesByWebId";
    
    @Context
    protected CoreSession session;
    
	@OperationMethod
	public Object run() throws ClientException {
		
        JSONObject duplicatedItems = new JSONObject();
        
        ElasticSearchAdmin esAdmin = Framework.getService(ElasticSearchAdmin.class);

        SearchRequestBuilder request = esAdmin.getClient()
				.prepareSearch(esAdmin.getIndexNameForRepository(session.getRepositoryName()))
				.setTypes("doc").setSearchType(SearchType.QUERY_THEN_FETCH);


		String NXQLClause = "select ecm:uuid from Document where ecm:isVersion = 0 and ecm:isProxy = 0";

		QueryBuilder queryBuilder = NxqlQueryConverter.toESQueryBuilder(NXQLClause, session);

		request.setQuery(queryBuilder);
		// Sum aggregation
		TermsBuilder aggregation = AggregationBuilders.terms("top_ttc:webid").field("ttc:webid").size(10000)
				.order(Terms.Order.aggregation("_count", false));;

		request.addAggregation(aggregation);

		SearchResponse response = request.get();

		Map<String, Aggregation> results = response.getAggregations().asMap();
	    StringTerms topField = (StringTerms) results.get("top_ttc:webid");

	    JSONArray docs = new JSONArray();

	    for(Terms.Bucket b : topField.getBuckets()) {

	    	if(b.getDocCount() > 1) {
		    	JSONObject doc = new JSONObject();
		    	doc.put("webid", b.getKey());
		    	doc.put("count", b.getDocCount());
			    docs.add(doc);
	    	}
	    }

    	duplicatedItems.put("docs", docs);

        return new StringBlob(duplicatedItems.toString(), "application/json");
	}
    

}
