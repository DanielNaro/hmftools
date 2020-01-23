package com.hartwig.hmftools.iclusion.api;

import java.util.List;

import com.squareup.moshi.Json;

class IclusionIndication {
    @Json(name = "id") public String id;
    @Json(name = "parent_id") public String parentId;
    @Json(name = "doid") public String doid;
    @Json(name = "doid2") public String doid2;
    @Json(name = "indication_name") public String indicationName;
    @Json(name = "indication_name_full") public String indicationNameFull;
    @Json(name = "node_ids") public List<String> nodeIds;
}
