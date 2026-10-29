package com.xiaoying.studio.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class TaskProductionIT {
  @Autowired MockMvc mvc;

  @Test
  void rejectsSimulationOverridesInProduction() throws Exception {
    var cookie =
        mvc.perform(post("/api/session"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    mvc.perform(
            post("/api/tasks")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":\"demo-1\",\"assetIds\":[],\"simulation\":{}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SIMULATION_DISABLED"));
  }

  @Test
  void productionDisablesFakeVerificationAndRegistration() throws Exception {
    var cookie =
        mvc.perform(post("/api/session"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    mvc.perform(
            post("/api/auth/demo-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"email\",\"destination\":\"a@example.com\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_DISABLED"));
    mvc.perform(
            post("/api/auth/register")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"a@example.com\",\"phone\":\"13800138000\",\"password\":\"password123\",\"emailCode\":\"123456\",\"phoneCode\":\"123456\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_DISABLED"));
    mvc.perform(
            post("/api/auth/register")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"phone\",\"destination\":\"13800138000\",\"password\":\"password123\",\"code\":\"123456\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_DISABLED"));
    mvc.perform(
            post("/api/auth/bind")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"phone\",\"destination\":\"13800138000\",\"code\":\"123456\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_DISABLED"));
  }
}
