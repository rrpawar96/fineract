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
package org.apache.fineract.infrastructure.creditbureau.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.creditbureau.data.CreditReportData;
import org.apache.fineract.infrastructure.creditbureau.domain.CreditBureauConfiguration;
import org.apache.fineract.infrastructure.creditbureau.domain.CreditBureauConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.creditbureau.domain.CreditBureauToken;
import org.apache.fineract.infrastructure.creditbureau.domain.TokenRepositoryWrapper;
import org.apache.fineract.infrastructure.creditbureau.serialization.CreditBureauTokenCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Component
@Service
public class ThitsaWorksCreditBureauIntegrationWritePlatformServiceImpl implements ThitsaWorksCreditBureauIntegrationWritePlatformService {

    private final PlatformSecurityContext context;
    private final FromJsonHelper fromApiJsonHelper;
    private final TokenRepositoryWrapper tokenRepository;
    private final CreditBureauConfigurationRepositoryWrapper configDataRepository;
    private final CreditBureauTokenCommandFromApiJsonDeserializer fromApiJsonDeserializer;

    @Autowired
    public ThitsaWorksCreditBureauIntegrationWritePlatformServiceImpl(final PlatformSecurityContext context,
            final FromJsonHelper fromApiJsonHelper, final TokenRepositoryWrapper tokenRepository,
            final CreditBureauConfigurationRepositoryWrapper configDataRepository,
            final CreditBureauTokenCommandFromApiJsonDeserializer fromApiJsonDeserializer) {
        this.context = context;
        this.tokenRepository = tokenRepository;
        this.configDataRepository = configDataRepository;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ThitsaWorksCreditBureauIntegrationWritePlatformServiceImpl.class);

    @Transactional
    @Override
    public String httpConnectionMethod(String process, String nrcID, String userName, String password, String subscriptionKey,
            String subscriptionId, String url, String token, Long uniqueID, File report) {

        String result = null;

        try {
            String post_params = null;
            HttpURLConnection httpConnection = this.process(process, nrcID, userName, password, subscriptionKey, subscriptionId, url, token,
                    uniqueID, report);

            if (process.equals("token")) {
                post_params = "" + "BODY=x-www-form-urlencoded&\r" + "grant_type=password&\r" + "userName=" + userName + "&\r" + "password="
                        + password + "&\r";
            } else if (process.equals("NRC")) {

                post_params = "BODY=x-www-form-urlencoded&nrc=" + nrcID + "&";
            } else if (process.equals("UploadCreditReport")) {

                post_params = "BODY=formdata&" + report + "&" + "userName=" + userName + "&";
            }

            // token header not used when creating token i.e. when token will be null
            if (token != null) {
                httpConnection.setRequestProperty("Authorization", "Bearer " + token);
            }

            // this set is required only for (POST METHOD)- fetching uniqueID from NRC/Creating Token/Add Credit report
            if (process.equals("NRC") || process.equals("token") || process.equals("UploadCreditReport")) {
                httpConnection.setDoOutput(true);
                OutputStream os = httpConnection.getOutputStream();
                os.write(post_params.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }

            result = this.httpResponse(httpConnection);

        } catch (IOException e) {
            LOG.error("Error occured.", e);
        }
        return result;

    }

    private HttpURLConnection process(String process, String nrcID, String userName, String password, String subscriptionKey,
            String subscriptionId, String url, String token, Long uniqueID, File report) {

        HttpURLConnection httpConnection = null;

        try {
            if (process.equals("token")) {

                URL tokenurl = new URL(url);
                httpConnection = (HttpURLConnection) tokenurl.openConnection();
                httpConnection.setRequestMethod("POST");

            } else if (process.equals("NRC")) {

                URL NrcURL = new URL(url + nrcID);
                httpConnection = (HttpURLConnection) NrcURL.openConnection();
                httpConnection.setRequestMethod("POST");

            } else if (process.equals("CreditReport")) {

                URL CreditReportURL = new URL(url + uniqueID);
                httpConnection = (HttpURLConnection) CreditReportURL.openConnection();
                httpConnection.setRequestMethod("GET");

            } else if (process.equals("UploadCreditReport")) {

                URL addCreditReporturl = new URL(url);
                httpConnection = (HttpURLConnection) addCreditReporturl.openConnection();
                httpConnection.setRequestMethod("POST");
            }

            // common set of headers
            httpConnection.setRequestProperty("mcix-subscription-key", subscriptionKey);
            httpConnection.setRequestProperty("mcix-subscription-id", subscriptionId);
            httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        } catch (IOException e) {
            LOG.error("Error occured.", e);
        }
        return httpConnection;
    }

    public String httpResponse(HttpURLConnection httpConnection) {

        String result = null; // return type of this method
        try {
            int responseCode = httpConnection.getResponseCode();

            StringBuilder response = new StringBuilder();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                response = new StringBuilder();

                LOG.info("----- RESPONSE OK-----");

                BufferedReader in = new BufferedReader(new InputStreamReader(httpConnection.getInputStream(), StandardCharsets.UTF_8));

                String readLine = null;
                while ((readLine = in.readLine()) != null) {
                    response.append(readLine);
                }
                in.close();
                result = response.toString();
                LOG.info("----- result-----{}", result);

            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                LOG.info("-----IP FORBIDDEN-----");
                String httpResponse = "HTTP_UNAUTHORIZED";
                this.handleAPIIntegrityIssues(httpResponse);

            } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                LOG.info("-----IP FORBIDDEN-----");
                String httpResponse = "HTTP_FORBIDDEN";
                this.handleAPIIntegrityIssues(httpResponse);

            } else {
                LOG.info("Request is Invalid");
            }

        } catch (IOException e) {
            LOG.error("Error occured.", e);
        }
        return result;
    }

    @Transactional
    @Override
    @SuppressWarnings("StringSplitter")
    public CreditReportData getCreditReportFromThitsaWorks(final JsonCommand command) {

        this.context.authenticatedUser();
        String nrcId = command.stringValueOfParameterNamed("NRC");
        String bureauID = command.stringValueOfParameterNamed("creditBureauID");
        Integer creditBureauId = Integer.parseInt(bureauID);

        CreditBureauConfiguration subscriptionIdData = this.configDataRepository.getCreditBureauConfigData(creditBureauId,
                "SubscriptionId");
        CreditBureauConfiguration subscriptionKeyData = this.configDataRepository.getCreditBureauConfigData(creditBureauId,
                "SubscriptionKey");
        CreditBureauConfiguration userNameData = this.configDataRepository.getCreditBureauConfigData(creditBureauId, "Username");
        CreditBureauConfiguration passwordData = this.configDataRepository.getCreditBureauConfigData(creditBureauId, "Password");

        String subscriptionId = "";
        String subscriptionKey = "";
        String userName = "";
        String password = "";

        try {
            subscriptionId = subscriptionIdData.getValue();
            subscriptionKey = subscriptionKeyData.getValue();
            userName = userNameData.getValue();
            password = passwordData.getValue();
        } catch (NullPointerException ex) {
            throw new PlatformDataIntegrityException("Credit Bureau Configuration is not available",
                    "Credit Bureau Configuration is not available" + ex);
        }

        String token = null;
        if (!"".equals(subscriptionId) && !"".equals(subscriptionKey) && !"".equals(userName) && !"".equals(password)) {
            token = createToken(userName, password, subscriptionId, subscriptionKey, creditBureauId);
        } else {
            throw new PlatformDataIntegrityException("Credit Bureau Configuration is not available",
                    "Credit Bureau Configuration is not available");
        }

        // will use only "NRC" part of code from common http method to get data based on nrc
        String process = "NRC";
        CreditBureauConfiguration SearchURL = this.configDataRepository.getCreditBureauConfigData(creditBureauId, "searchurl");
        String url = SearchURL.getValue();
        String result = this.httpConnectionMethod(process, nrcId, userName, password, subscriptionKey, subscriptionId, url, token, 0L,
                null);

        // after fetching the data from httpconnection it will be come back here for fetching UniqueID from data
        if (process.equals("NRC")) {

            JsonObject reportObject = JsonParser.parseString(result).getAsJsonObject();

            JsonElement element = reportObject.get("Data");

            if (element.isJsonNull()) {
                String ResponseMessage = reportObject.get("ResponseMessage").getAsString();
                handleAPIIntegrityIssues(ResponseMessage);
            }

            // to fetch the Unique ID from Result
            JsonObject jsonObject = JsonParser.parseString(result).getAsJsonObject();
            Long uniqueID = 0L;
            try {
                JsonArray jArray = jsonObject.getAsJsonArray("Data");
                JsonObject jobject = jArray.get(0).getAsJsonObject();
                String uniqueIdString = jobject.get("UniqueID").toString();

                String TrimUniqueId = uniqueIdString.substring(1, uniqueIdString.length() - 1);
                uniqueID = Long.parseLong(TrimUniqueId);
            } catch (IndexOutOfBoundsException e) {
                String ResponseMessage = reportObject.get("ResponseMessage").getAsString();
                handleAPIIntegrityIssues(ResponseMessage);
            }

            process = "CreditReport";
            CreditBureauConfiguration creditReportURL = this.configDataRepository.getCreditBureauConfigData(creditBureauId,
                    "creditReporturl");
            url = creditReportURL.getValue();
            result = this.httpConnectionMethod(process, nrcId, userName, password, subscriptionKey, subscriptionId, url, token, uniqueID,
                    null);

        }

        // after getting the result(creditreport) from httpconnection-response it will assign creditreport to generic
        // creditreportdata object

        JsonObject reportObject = JsonParser.parseString(result).getAsJsonObject();

        JsonObject borrowerInfos = null;
        String borrowerInfo = null;
        String CreditScore = null;
        String ActiveLoans = null;
        String PaidLoans = null;

        // Credit Reports Stored into Generic CreditReportData
        JsonObject data = null;
        JsonElement element = reportObject.get("Data");

        if (!(element instanceof JsonNull)) { // NOTE : "element instanceof JsonNull" is for handling empty values (and
                                              // assigning null) while fetching data from results
            data = (JsonObject) element;
        }

        borrowerInfo = null;
        element = data.get("BorrowerInfo");
        if (!(element instanceof JsonNull)) {
            borrowerInfos = (JsonObject) element;

            Gson gson = new Gson();
            borrowerInfo = gson.toJson(borrowerInfos);
        }

        String Name = borrowerInfos.get("Name").toString();
        String Gender = borrowerInfos.get("Gender").toString();
        String Address = borrowerInfos.get("Address").toString();

        element = data.get("CreditScore");
        if (!(element instanceof JsonNull)) {
            JsonObject Score = (JsonObject) element;

            Gson gson = new Gson();
            CreditScore = gson.toJson(Score);
        }

        element = data.get("ActiveLoans");
        if (!(element instanceof JsonNull)) {
            JsonArray ActiveLoan = (JsonArray) element;

            Gson gson = new Gson();
            ActiveLoans = gson.toJson(ActiveLoan);
        }

        element = data.get("WriteOffLoans");
        if (!(element instanceof JsonNull)) {
            JsonArray PaidLoan = (JsonArray) element;

            Gson gson = new Gson();
            PaidLoans = gson.toJson(PaidLoan);
        }

        return CreditReportData.instance(Name, Gender, Address, CreditScore, borrowerInfo, ActiveLoans, PaidLoans);
    }

    @Transactional
    @Override
    public String createToken(String userName, String password, String subscriptionId, String subscriptionKey, Integer creditBureauId) {

        CreditBureauToken creditbureautoken = this.tokenRepository.getToken();

        // check the expiry date of the previous token.
        if (creditbureautoken != null) {
            Date current = new Date();
            Date getExpiryDate = creditbureautoken.getTokenExpiryDate();

            if (getExpiryDate.before(current)) {
                this.tokenRepository.delete(creditbureautoken);
                creditbureautoken = null;
            }
        }
        // storing token if it is valid token(not expired)
        String token = null;
        if (creditbureautoken != null) {
            token = creditbureautoken.getCurrentToken();
        }

        if (creditbureautoken == null) {
            CreditBureauConfiguration tokenURL = this.configDataRepository.getCreditBureauConfigData(creditBureauId, "tokenurl");
            String url = tokenURL.getValue();

            String process = "token";
            String nrcId = null;
            Long uniqueID = 0L;
            String result = this.httpConnectionMethod(process, nrcId, userName, password, subscriptionKey, subscriptionId, url, token,
                    uniqueID, null);

            // created token will be storing it into database
            final CommandWrapper wrapper = new CommandWrapperBuilder().withJson(result).build();
            final String json = wrapper.getJson();

            JsonCommand apicommand = null;
            final JsonElement parsedCommand = this.fromApiJsonHelper.parse(json);

            apicommand = JsonCommand.from(json, parsedCommand, this.fromApiJsonHelper, wrapper.getEntityName(), wrapper.getEntityId(),
                    wrapper.getSubentityId(), wrapper.getGroupId(), wrapper.getClientId(), wrapper.getLoanId(), wrapper.getSavingsId(),
                    wrapper.getTransactionId(), wrapper.getHref(), wrapper.getProductId(), wrapper.getCreditBureauId(),
                    wrapper.getOrganisationCreditBureauId());

            this.fromApiJsonDeserializer.validateForCreate(apicommand.json());

            final CreditBureauToken generatedtoken = CreditBureauToken.fromJson(apicommand);

            final CreditBureauToken credittoken = this.tokenRepository.getToken();
            if (credittoken != null) {
                this.tokenRepository.delete(credittoken);
            }

            this.tokenRepository.save(generatedtoken);

            creditbureautoken = this.tokenRepository.getToken();
            token = creditbureautoken.getCurrentToken();

        }
        return token;
    }

    private void handleAPIIntegrityIssues(String httpResponse) {

        throw new PlatformDataIntegrityException(httpResponse, httpResponse);

    }

}
