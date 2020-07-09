/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.creditbureau.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditReportRepository extends JpaRepository<CreditReport, Long>, JpaSpecificationExecutor<CreditReport> {

    @Query("SELECT creditBureauReport from CreditReport creditBureauReport where creditBureauReport.nrc = :nrc and creditBureauReport.creditBureauId = :creditBureauId ")
    CreditReport getThitsaWorksCreditReport(@Param("creditBureauId") Long creditBureauId, @Param("nrc") String nrc);
    // Since creditReportNumber has became nrc, this query will only be used to fetch ThitsaWorks CreditBureau Reports.

}
