/**
 *  JsonResponseWriter
 *  Copyright 2012 by Michael Peter Christen
 *  First released 10.09.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.services.federated.solr.OpensearchResponseWriter.ResHead;
import net.yacy.search.index.YaCySchema;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;

import de.anomic.server.serverObjects;

/**
 * write the opensearch result in YaCys special way to include as much as in opensearch is included.
 * This will also include YaCy facets.
 */
public class JsonResponseWriter implements QueryResponseWriter {

    private String title;

    public JsonResponseWriter() {
        super();
    }

    public void setTitle(String searchPageTitle) {
        this.title = searchPageTitle;
    }

    @Override
    public String getContentType(final SolrQueryRequest request, final SolrQueryResponse response) {
        return "application/json; charset=UTF-8";
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList n) {
    }

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        assert rsp.getValues().get("responseHeader") != null;
        assert rsp.getValues().get("response") != null;

        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> responseHeader = (SimpleOrderedMap<Object>) rsp.getResponseHeader();
        DocSlice response = (DocSlice) rsp.getValues().get("response");
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> highlighting = (SimpleOrderedMap<Object>) rsp.getValues().get("highlighting");
        Map<String, List<String>> snippets = OpensearchResponseWriter.highlighting(highlighting);

        // parse response header
        ResHead resHead = new ResHead();
        NamedList<?> val0 = (NamedList<?>) responseHeader.get("params");
        resHead.rows = Integer.parseInt((String) val0.get("rows"));
        resHead.offset = response.offset(); // equal to 'start'
        resHead.numFound = response.matches();

        // write header
        writer.write(("{\"channels\": [{\n").toCharArray());
        solitaireTag(writer, "totalResults", Integer.toString(resHead.numFound));
        solitaireTag(writer, "startIndex", Integer.toString(resHead.offset));
        solitaireTag(writer, "itemsPerPage", Integer.toString(resHead.rows));
        solitaireTag(writer, "title", this.title);
        solitaireTag(writer, "description", "Search Result");
        writer.write("\"items\": [\n".toCharArray());

        // parse body
        final int responseCount = response.size();
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        String urlhash = null;
        for (int i = 0; i < responseCount; i++) {
            writer.write("{\n".toCharArray());
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, OpensearchResponseWriter.SOLR_FIELDS);
            List<Fieldable> fields = doc.getFields();
            int fieldc = fields.size();
            List<String> texts = new ArrayList<String>();
            String description = "", title = "";
            StringBuilder path = new StringBuilder(80);
            for (int j = 0; j < fieldc; j++) {
                Fieldable value = fields.get(j);
                String fieldName = value.name();
                if (YaCySchema.title.name().equals(fieldName)) {
                    title = value.stringValue();
                    texts.add(title);
                    continue;
                }
                if (YaCySchema.sku.name().equals(fieldName)) {
                    solitaireTag(writer, "link", value.stringValue());
                    continue;
                }
                if (YaCySchema.description.name().equals(fieldName)) {
                    description = value.stringValue();
                    texts.add(description);
                    continue;
                }
                if (YaCySchema.id.name().equals(fieldName)) {
                    urlhash = value.stringValue();
                    solitaireTag(writer, "guid", urlhash);
                    continue;
                }
                if (YaCySchema.host_s.name().equals(fieldName)) {
                    solitaireTag(writer, "host", value.stringValue());
                    continue;
                }
                if (YaCySchema.url_paths_sxt.name().equals(fieldName)) {
                    path.append('/').append(value.stringValue());
                    continue;
                }
                if (YaCySchema.last_modified.name().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    solitaireTag(writer, "pubDate", HeaderFramework.formatRFC1123(d));
                    continue;
                }
                if (YaCySchema.size_i.name().equals(fieldName)) {
                    int size = value.stringValue() != null && value.stringValue().length() > 0 ? Integer.parseInt(value.stringValue()) : -1;
                    int sizekb = size / 1024;
                    int sizemb = sizekb / 1024;
                    solitaireTag(writer, "size", value.stringValue());
                    solitaireTag(writer, "sizename", sizemb > 0 ? (Integer.toString(sizemb) + " mbyte") : sizekb > 0 ? (Integer.toString(sizekb) + " kbyte") : (Integer.toString(size) + " byte"));
                    continue;
                }
                if (YaCySchema.text_t.name().equals(fieldName)) {
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.h1_txt.name().equals(fieldName) || YaCySchema.h2_txt.name().equals(fieldName) ||
                    YaCySchema.h3_txt.name().equals(fieldName) || YaCySchema.h4_txt.name().equals(fieldName) ||
                    YaCySchema.h5_txt.name().equals(fieldName) || YaCySchema.h6_txt.name().equals(fieldName)) {
                    // because these are multi-valued fields, there can be several of each
                    texts.add(value.stringValue());
                    continue;
                }
            }
            // compute snippet from texts

            solitaireTag(writer, "path", path.toString());
            solitaireTag(writer, "title", title.length() == 0 ? (texts.size() == 0 ? path.toString() : texts.get(0)) : title);
            List<String> snippet = urlhash == null ? null : snippets.get(urlhash);
            writer.write("\"description\":\""); writer.write(serverObjects.toJSON(snippet == null || snippet.size() == 0 ? description : snippet.get(0))); writer.write("\"\n}\n");
            if (i < responseCount - 1) {
                writer.write(",\n".toCharArray());
            }
        }
        writer.write("]\n".toCharArray());
        writer.write(",\n\"navigation\":[\n");
        writer.write("{\"facetname\":\"filetypes\",\"displayname\":\"Filetypes\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[]},\n".toCharArray());
        writer.write("{\"facetname\":\"protocols\",\"displayname\":\"Protocol\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[]},\n".toCharArray());
        writer.write("{\"facetname\":\"domains\",\"displayname\":\"Domains\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[]},\n".toCharArray());
        writer.write("{\"facetname\":\"topics\",\"displayname\":\"Topics\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[]}\n".toCharArray());
        writer.write("]}]}\n".toCharArray());
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write('"'); writer.write(tagname); writer.write("\":\""); writer.write(serverObjects.toJSON(value)); writer.write("\","); writer.write('\n');
    }

}

/**
{
  "channels": [{
    "title": "YaCy P2P-Search for uni-mainz",
    "description": "Search for uni-mainz",
    "link": "http://localhost:8090/yacysearch.html?query=uni-mainz&amp;resource=local&amp;contentdom=text&amp;verify=-UNRESOLVED_PATTERN-",
    "image": {
      "url": "http://localhost:8090/env/grafics/yacy.gif",
      "title": "Search for uni-mainz",
      "link": "http://localhost:8090/yacysearch.html?query=uni-mainz&amp;resource=local&amp;contentdom=text&amp;verify=-UNRESOLVED_PATTERN-"
    },
    "totalResults": "1986",
    "startIndex": "0",
    "itemsPerPage": "10",
    "searchTerms": "uni-mainz",

    "items": [

    {
      "title": "From dark matter to school experiments: Physicists meet in Mainz",
      "link": "http://www.phmi.uni-mainz.de/5305.php",
      "code": "",
      "description": "",
      "pubDate": "Mon, 10 Sep 2012 10:25:36 +0000",
      "size": "15927",
      "sizename": "15 kbyte",
      "guid": "7NYsT4NwCWgB",
      "faviconCode": "d6ce1c0b",
      "host": "www.phmi.uni-mainz.de",
      "path": "/5305.php",
      "file": "/5305.php",
      "urlhash": "7NYsT4NwCWgB",
      "ranking": "6983282"
    }
    ,
    ..
  }],
"navigation": [
{
  "facetname": "filetypes",
  "displayname": "Filetype",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "php", "count": "8", "modifier": "filetype%3Aphp", "url": "/yacysearch.json?query=uni-mainz+filetype%3Aphp&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "html", "count": "1", "modifier": "filetype%3Ahtml", "url": "/yacysearch.json?query=uni-mainz+filetype%3Ahtml&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
},
{
  "facetname": "protocols",
  "displayname": "Protocol",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "http", "count": "13", "modifier": "%2Fhttp", "url": "/yacysearch.json?query=uni-mainz+%2Fhttp&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "https", "count": "1", "modifier": "%2Fhttps", "url": "/yacysearch.json?query=uni-mainz+%2Fhttps&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
},
{
  "facetname": "domains",
  "displayname": "Domains",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "www.geo.uni-frankfurt.de", "count": "1", "modifier": "site%3Awww.geo.uni-frankfurt.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.geo.uni-frankfurt.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.info.jogustine.uni-mainz.de", "count": "1", "modifier": "site%3Awww.info.jogustine.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.info.jogustine.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.fb09.uni-mainz.de", "count": "1", "modifier": "site%3Awww.fb09.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.fb09.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.studgen.uni-mainz.de", "count": "1", "modifier": "site%3Awww.studgen.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.studgen.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "twitter.com", "count": "1", "modifier": "site%3Atwitter.com", "url": "/yacysearch.json?query=uni-mainz+site%3Atwitter.com&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.theaterwissenschaft.uni-mainz.de", "count": "1", "modifier": "site%3Awww.theaterwissenschaft.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.theaterwissenschaft.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.uni-mainz.de", "count": "1", "modifier": "site%3Awww.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.fb06.uni-mainz.de", "count": "1", "modifier": "site%3Awww.fb06.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.fb06.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.familienservice.uni-mainz.de", "count": "1", "modifier": "site%3Awww.familienservice.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.familienservice.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.zdv.uni-mainz.de", "count": "1", "modifier": "site%3Awww.zdv.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.zdv.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "zope.verwaltung.uni-mainz.de", "count": "1", "modifier": "site%3Azope.verwaltung.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Azope.verwaltung.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.geo.uni-mainz.de", "count": "1", "modifier": "site%3Awww.geo.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.geo.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.bio.uni-mainz.de", "count": "1", "modifier": "site%3Awww.bio.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.bio.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.phmi.uni-mainz.de", "count": "1", "modifier": "site%3Awww.phmi.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.phmi.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
},
{
  "facetname": "topics",
  "displayname": "Topics",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "des", "count": "3", "modifier": "des", "url": "/yacysearch.json?query=uni-mainz+des&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "auf", "count": "2", "modifier": "auf", "url": "/yacysearch.json?query=uni-mainz+auf&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "willkommen", "count": "2", "modifier": "willkommen", "url": "/yacysearch.json?query=uni-mainz+willkommen&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "biologie", "count": "1", "modifier": "biologie", "url": "/yacysearch.json?query=uni-mainz+biologie&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "johannes", "count": "1", "modifier": "johannes", "url": "/yacysearch.json?query=uni-mainz+johannes&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "weiterleitung", "count": "1", "modifier": "weiterleitung", "url": "/yacysearch.json?query=uni-mainz+weiterleitung&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "archiv", "count": "1", "modifier": "archiv", "url": "/yacysearch.json?query=uni-mainz+archiv&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "entdecken", "count": "1", "modifier": "entdecken", "url": "/yacysearch.json?query=uni-mainz+entdecken&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "gutenberg", "count": "1", "modifier": "gutenberg", "url": "/yacysearch.json?query=uni-mainz+gutenberg&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "heimverzeichnis", "count": "1", "modifier": "heimverzeichnis", "url": "/yacysearch.json?query=uni-mainz+heimverzeichnis&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "school", "count": "1", "modifier": "school", "url": "/yacysearch.json?query=uni-mainz+school&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "vernetzung", "count": "1", "modifier": "vernetzung", "url": "/yacysearch.json?query=uni-mainz+vernetzung&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
}
],
"totalResults": "1986"
}]
}
*/