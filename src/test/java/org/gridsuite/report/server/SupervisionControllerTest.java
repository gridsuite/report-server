/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = {ReportApplication.class})
public class SupervisionControllerTest {

    public static final String URL_TEMPLATE = "/" + ReportApi.API_VERSION + "/reports/";
    public static final String URL_TEMPLATE_SUPERVISION = "/" + ReportApi.API_VERSION + "/supervision/";
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReportService reportService;

    @Before
    public void setUp() {
        Configuration.defaultConfiguration();
        MockitoAnnotations.initMocks(this);
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);

        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider(objectMapper);
            private final MappingProvider mappingProvider = new JacksonMappingProvider(objectMapper);

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });

    }

    @After
    public void tearDown() {
        reportService.deleteAll();
    }

    private static final String REPORT_UUID = "7165e1a1-6aa5-47a9-ba55-d1ee4e234d13";
    private static final String REPORT_SIMULATORS = "/reportSimulators.json";
    private static final String EXPECTED_DELETED_SIMULATORS = "/expectedDeletedSimulators.json";

    public String toString(String resourceName) {
        try {
            return new String(ByteStreams.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream(resourceName))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void test() throws Exception {
        String testReport1 = toString(REPORT_SIMULATORS);
        insertReport(REPORT_UUID, testReport1);

        mvc.perform(delete(URL_TEMPLATE_SUPERVISION + "loadflow")
                .queryParam("reportsList", REPORT_UUID))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(delete(URL_TEMPLATE_SUPERVISION + "sensitivity-analysis")
                .queryParam("reportsList", REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();

        mvc.perform(delete(URL_TEMPLATE_SUPERVISION + "security-analysis")
                .queryParam("reportsList", REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();

        mvc.perform(delete(URL_TEMPLATE_SUPERVISION + "shortcircuit")
                .queryParam("reportsList", REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();

        String expectedReport = toString(EXPECTED_DELETED_SIMULATORS);
        mvc.perform(get(URL_TEMPLATE))
            .andExpect(status().isOk())
            .andExpect(content().json(expectedReport));
    }

    private void insertReport(String reportsId, String content) throws Exception {
        mvc.perform(put(URL_TEMPLATE + reportsId)
                .content(content)
                .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }

}
