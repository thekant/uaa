/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.scim.endpoints;

import org.cloudfoundry.identity.uaa.TestClassNullifier;
import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.codestore.ExpiringCode;
import org.cloudfoundry.identity.uaa.codestore.ExpiringCodeStore;
import org.cloudfoundry.identity.uaa.error.ExceptionReportHttpMessageConverter;
import org.cloudfoundry.identity.uaa.scim.ScimMeta;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUserProvisioning;
import org.cloudfoundry.identity.uaa.scim.endpoints.PasswordResetEndpoints.PasswordChange;
import org.cloudfoundry.identity.uaa.scim.exception.InvalidPasswordException;
import org.cloudfoundry.identity.uaa.scim.validate.PasswordValidator;
import org.cloudfoundry.identity.uaa.test.MockAuthentication;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;

import static org.cloudfoundry.identity.uaa.scim.endpoints.PasswordResetEndpoints.PASSWORD_RESET_LIFETIME;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PasswordResetEndpointsTest extends TestClassNullifier {

    private MockMvc mockMvc;
    private ScimUserProvisioning scimUserProvisioning;
    private ExpiringCodeStore expiringCodeStore;
    private PasswordValidator passwordValidator;

    @Before
    public void setUp() throws Exception {
        scimUserProvisioning = mock(ScimUserProvisioning.class);
        expiringCodeStore = mock(ExpiringCodeStore.class);
        passwordValidator = mock(PasswordValidator.class);
        PasswordResetEndpoints controller = new PasswordResetEndpoints(scimUserProvisioning, expiringCodeStore, passwordValidator);
        controller.setMessageConverters(new HttpMessageConverter[] { new ExceptionReportHttpMessageConverter() });
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(expiringCodeStore.generateCode(eq("id001"), any(Timestamp.class)))
                .thenReturn(new ExpiringCode("secret_code", new Timestamp(System.currentTimeMillis() + PASSWORD_RESET_LIFETIME), "id001"));

        PasswordChange change = new PasswordChange("id001", "user@example.com");
        when(expiringCodeStore.generateCode(eq(JsonUtils.writeValueAsString(change)), any(Timestamp.class)))
            .thenReturn(new ExpiringCode("secret_code", new Timestamp(System.currentTimeMillis() + PASSWORD_RESET_LIFETIME), "id001"));

        change = new PasswordChange("id001", "user\"'@example.com");
        when(expiringCodeStore.generateCode(eq(JsonUtils.writeValueAsString(change)), any(Timestamp.class)))
            .thenReturn(new ExpiringCode("secret_code", new Timestamp(System.currentTimeMillis() + PASSWORD_RESET_LIFETIME), "id001"));

    }

    @Test
    public void testCreatingAPasswordResetWhenTheUsernameExists() throws Exception {
        ScimUser user = new ScimUser("id001", "user@example.com", null, null);
        user.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        user.addEmail("user@example.com");
        when(scimUserProvisioning.query("userName eq \"user@example.com\" and origin eq \"" + Origin.UAA + "\""))
                .thenReturn(Arrays.asList(user));

        MockHttpServletRequestBuilder post = post("/password_resets")
                .contentType(APPLICATION_JSON)
                .content("user@example.com")
                .accept(APPLICATION_JSON);

        mockMvc.perform(post)
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"code\":\"secret_code\"")))
                .andExpect(content().string(containsString("\"user_id\":\"id001\"")));
    }

    @Test
    public void testCreatingAPasswordResetWhenTheUserDoesNotExist() throws Exception {
        when(scimUserProvisioning.query("userName eq \"user@example.com\" and origin eq \"" + Origin.UAA + "\""))
                .thenReturn(Arrays.<ScimUser>asList());

        MockHttpServletRequestBuilder post = post("/password_resets")
                .contentType(APPLICATION_JSON)
                .content("user@example.com")
                .accept(APPLICATION_JSON);

        mockMvc.perform(post)
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCreatingAPasswordResetWhenTheUserHasNonUaaOrigin() throws Exception {
        when(scimUserProvisioning.query("userName eq \"user@example.com\" and origin eq \"" + Origin.UAA + "\""))
            .thenReturn(Arrays.<ScimUser>asList());

        ScimUser user = new ScimUser("id001", "user@example.com", null, null);
        user.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        user.addEmail("user@example.com");
        user.setOrigin(Origin.LDAP);
        when(scimUserProvisioning.query("userName eq \"user@example.com\""))
            .thenReturn(Arrays.<ScimUser>asList(user));

        MockHttpServletRequestBuilder post = post("/password_resets")
            .contentType(APPLICATION_JSON)
            .content("user@example.com")
            .accept(APPLICATION_JSON);

        mockMvc.perform(post)
            .andExpect(status().isConflict())
            .andExpect(content().string(containsString("\"user_id\":\"id001\"")));
    }

    @Test
    public void testCreatingAPasswordResetWithAUsernameContainingSpecialCharacters() throws Exception {
        ScimUser user = new ScimUser("id001", "user\"'@example.com", null, null);
        user.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        user.addEmail("user\"'@example.com");
        when(scimUserProvisioning.query("userName eq \"user\\\"'@example.com\" and origin eq \"" + Origin.UAA + "\""))
            .thenReturn(Arrays.asList(user));

        MockHttpServletRequestBuilder post = post("/password_resets")
            .contentType(APPLICATION_JSON)
            .content("user\"'@example.com")
            .accept(APPLICATION_JSON);

        mockMvc.perform(post)
            .andExpect(status().isCreated())
            .andExpect(content().string(containsString("\"code\":\"secret_code\"")))
            .andExpect(content().string(containsString("\"user_id\":\"id001\"")));


        when(scimUserProvisioning.query("userName eq \"user\\\"'@example.com\" and origin eq \"" + Origin.UAA + "\""))
            .thenReturn(Arrays.<ScimUser>asList());
        user.setOrigin(Origin.LDAP);
        when(scimUserProvisioning.query("userName eq \"user\\\"'@example.com\""))
            .thenReturn(Arrays.asList(user));

        post = post("/password_resets")
            .contentType(APPLICATION_JSON)
            .content("user\"'@example.com")
            .accept(APPLICATION_JSON);

        mockMvc.perform(post)
            .andExpect(status().isConflict());
    }

    @Test
    public void testChangingAPasswordWithAValidCode() throws Exception {
        when(expiringCodeStore.retrieveCode("secret_code"))
                .thenReturn(new ExpiringCode("secret_code", new Timestamp(System.currentTimeMillis()+ PASSWORD_RESET_LIFETIME), "eyedee"));

        ScimUser scimUser = new ScimUser("eyedee", "user@example.com", "User", "Man");
        scimUser.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        scimUser.addEmail("user@example.com");
        when(scimUserProvisioning.retrieve("eyedee")).thenReturn(scimUser);

        MockHttpServletRequestBuilder post = post("/password_change")
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"secret_code\",\"new_password\":\"new_secret\"}")
                .accept(APPLICATION_JSON);

        SecurityContextHolder.getContext().setAuthentication(new MockAuthentication());

        mockMvc.perform(post)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("eyedee"))
                .andExpect(jsonPath("$.username").value("user@example.com"));

        Mockito.verify(scimUserProvisioning).changePassword("eyedee", null, "new_secret");
    }

    @Test
    public void testChangingAPasswordForUnverifiedUser() throws Exception {
        when(expiringCodeStore.retrieveCode("secret_code"))
                .thenReturn(new ExpiringCode("secret_code", new Timestamp(System.currentTimeMillis()+ PASSWORD_RESET_LIFETIME), "eyedee"));

        ScimUser scimUser = new ScimUser("eyedee", "user@example.com", "User", "Man");
        scimUser.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        scimUser.addEmail("user@example.com");
        scimUser.setVerified(false);
        when(scimUserProvisioning.retrieve("eyedee")).thenReturn(scimUser);

        MockHttpServletRequestBuilder post = post("/password_change")
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"secret_code\",\"new_password\":\"new_secret\"}")
                .accept(APPLICATION_JSON);

        SecurityContextHolder.getContext().setAuthentication(new MockAuthentication());

        mockMvc.perform(post)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("eyedee"))
                .andExpect(jsonPath("$.username").value("user@example.com"));

        Mockito.verify(scimUserProvisioning).changePassword("eyedee", null, "new_secret");
        Mockito.verify(scimUserProvisioning).verifyUser(scimUser.getId(), -1);
    }

    @Test
    public void testChangingAPasswordWithAUsernameAndPassword() throws Exception {
        ScimUser user = new ScimUser("id001", "user@example.com", null, null);
        user.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        user.addEmail("user@example.com");
        when(scimUserProvisioning.query("userName eq \"user@example.com\""))
                .thenReturn(Arrays.asList(user));
        when(scimUserProvisioning.checkPasswordMatches(anyString(), anyString())).thenReturn(false);
        MockHttpServletRequestBuilder post = post("/password_change")
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"user@example.com\",\"current_password\":\"secret\",\"new_password\":\"new_secret\"}")
                .accept(APPLICATION_JSON);

        SecurityContextHolder.getContext().setAuthentication(new MockAuthentication());

        mockMvc.perform(post)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("id001"))
                .andExpect(jsonPath("$.username").value("user@example.com"));

        Mockito.verify(scimUserProvisioning).changePassword("id001", "secret", "new_secret");
    }

    @Test
    public void testChangingAPasswordWithABadRequest() throws Exception {
        when(scimUserProvisioning.checkPasswordMatches(anyString(), anyString())).thenReturn(false);
        MockHttpServletRequestBuilder post = post("/password_change")
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"emailed_code\",\"username\":\"user@example.com\",\"current_password\":\"secret\",\"new_password\":\"new_secret\"}")
                .accept(APPLICATION_JSON);

        mockMvc.perform(post)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testPasswordsMustSatisfyPolicy() throws Exception {
        when(scimUserProvisioning.checkPasswordMatches(anyString(), anyString())).thenReturn(false);
        when(passwordValidator.validate("new_secret")).thenThrow(new InvalidPasswordException("Password flunks policy"));
        MockHttpServletRequestBuilder post = post("/password_change")
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"emailed_code\",\"username\":\"user@example.com\",\"current_password\":\"secret\",\"new_password\":\"new_secret\"}")
                .accept(APPLICATION_JSON);

        mockMvc.perform(post)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string("{\"message\":\"Password flunks policy\",\"error\":\"invalid_password\"}"));
    }

    @Test
    public void changePassword_ThrowsInvalidPasswordException_WhenNewPasswordSameAsOld() throws Exception {
        when(scimUserProvisioning.checkPasswordMatches(anyString(), anyString())).thenReturn(true);
        ScimUser user = new ScimUser("id001", "user@example.com", null, null);
        user.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        user.addEmail("user@example.com");
        when(scimUserProvisioning.query("userName eq \"user@example.com\""))
            .thenReturn(Arrays.asList(user));

        MockHttpServletRequestBuilder post = post("/password_change")
            .contentType(APPLICATION_JSON)
            .content("{\"username\":\"user@example.com\",\"current_password\":\"secret\",\"new_password\":\"secret\"}")
            .accept(APPLICATION_JSON);

        mockMvc.perform(post).andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void changePassword_ThrowsInvalidPasswordException_WhenNewPasswordSameAsOldWithCode() throws Exception {
        when(scimUserProvisioning.checkPasswordMatches(anyString(), anyString())).thenReturn(true);
        when(expiringCodeStore.retrieveCode("emailed_code"))
            .thenReturn(new ExpiringCode("emailed_code", new Timestamp(System.currentTimeMillis() + PASSWORD_RESET_LIFETIME), "eyedee"));
        when(scimUserProvisioning.checkPasswordMatches(anyString(), anyString())).thenReturn(true);
        ScimUser user = new ScimUser("eyedee", "user@example.com", null, null);
        user.setMeta(new ScimMeta(new Date(System.currentTimeMillis()-(1000*60*60*24)), new Date(System.currentTimeMillis()-(1000*60*60*24)), 0));
        user.addEmail("user@example.com");
        when(scimUserProvisioning.retrieve("eyedee")).thenReturn(user);

        MockHttpServletRequestBuilder post = post("/password_change")
            .contentType(APPLICATION_JSON)
            .content("{\"code\":\"emailed_code\", \"new_password\":\"secret\"}")
            .accept(APPLICATION_JSON);

        mockMvc.perform(post).andExpect(status().isUnprocessableEntity());
    }
}
