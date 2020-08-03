package com.info7255.controller;


import com.info7255.service.AuthorizeService;
import com.info7255.service.PlanService;
import com.info7255.validator.JsonValidator;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.validation.Valid;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;


@RestController
@RequestMapping(path = "/")
public class InsurancePlanController {

    @Autowired
    JsonValidator validator;

    @Autowired
    PlanService planservice;

    @Autowired
    private AuthorizeService authorizeService;

    @GetMapping(value = "/getToken")
    public ResponseEntity<String> getToken()
            throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {

        String token = null;
        try {
            token = authorizeService.getToken();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return new ResponseEntity<String>(token, HttpStatus.CREATED);
    }

    @PostMapping(path ="/plan", produces = "application/json")
    public ResponseEntity<Object> createPlan(@RequestHeader HttpHeaders headers, @Valid @RequestBody(required = false) String medicalPlan) throws Exception {
        if (medicalPlan == null || medicalPlan.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", "Body is Empty. Kindly provide the JSON").toString());
        }

        String returnValue = authorizeService.authorizeToken(headers);
        if ((returnValue != "Valid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());

        JSONObject plan = new JSONObject(medicalPlan);
        try{
            validator.validateJson(plan);
        }catch(ValidationException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error",ex.getErrorMessage()).toString());
        }

        //create a key for plan: objecyType + objectID
        String key = plan.get("objectType").toString() + "_" + plan.get("objectId").toString();
        //check if plan exists
        if(planservice.isKeyExists(key)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan already exist").toString());
        }

        //save the plan if not exist
        String newEtag = planservice.addPlanETag(plan, key);
        String res = "{ObjectId: " + plan.get("objectId") + ", ObjectType: " + plan.get("objectType") + "}";
        return ResponseEntity.ok().eTag(newEtag).body(new JSONObject(res).toString());//TODO: test
    }

    @PatchMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> patchPlan(@RequestHeader HttpHeaders headers, @Valid @RequestBody String medicalPlan, @PathVariable String objectId) throws IOException {

//        String returnValue = authorizeService.authorizeToken(headers);
//        if ((returnValue != "Valid Token"))
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());

        JSONObject plan = new JSONObject(medicalPlan);
        String key = "plan_" + objectId;
        if (!planservice.isKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        // return status 412 if a mid-air update occurs (e.g. etag/header is different from etag/in-processing)
        String actualEtag = planservice.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).build();
        }

        //update if the plan already created
        String newEtag = planservice.addPlanETag(plan, key);
        return ResponseEntity.ok().eTag(newEtag).body(new JSONObject().put("Message ", "Updated successfully").toString());
    }


    @GetMapping(path = "/{type}/{objectId}",produces = "application/json ")
    public ResponseEntity<Object> getPlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId,@PathVariable String type) throws JSONException, Exception {

        String key = type + "_" + objectId;
        if (!planservice.isKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        String actualEtag = null;
        if (type.equals("plan")) {
            actualEtag = planservice.getEtag(type + "_" + objectId, "eTag");
            String eTag = headers.getFirst("if-none-match");
            //if not updated -> 304
            if (actualEtag.equals(eTag)){
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(actualEtag).build();
            }
        }

        Map<String, Object> plan = planservice.getPlan(key);
        if (type.equals("plan")) {
            return ResponseEntity.ok().eTag(actualEtag).body(new JSONObject(plan).toString());
        }

        return ResponseEntity.ok().body(new JSONObject(plan).toString());
    }

    @PutMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> updatePlan(@RequestHeader HttpHeaders headers, @Valid @RequestBody String medicalPlan, @PathVariable String objectId) throws IOException {

//        String returnValue = authorizeService.authorizeToken(headers);
//        if ((returnValue != "Valid Token"))
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());

        JSONObject plan = new JSONObject(medicalPlan);
        try {
            validator.validateJson(plan);
        } catch (ValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Validation Error", ex.getMessage()).toString());
        }

        String key = "plan_" + objectId;
        //check if the target for update exist
        if (!planservice.isKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        // return status 412 if a mid-air update occurs (e.g. etag/header is different from etag/in-processing)
        String actualEtag = planservice.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).build();
        }

        planservice.deletePlan("plan" + "_" + objectId);
        String newEtag = planservice.addPlanETag(plan, key);
        return ResponseEntity.ok().eTag(newEtag).body(new JSONObject().put("Message: ", "Updated successfully").toString());
    }


    @DeleteMapping("/plan/{objectId}")
    public ResponseEntity<Object> getPlan(@PathVariable String objectId){

        if (!planservice.isKeyExists("plan"+ "_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        planservice.deletePlan("plan" + "_" + objectId);
        return ResponseEntity.noContent().build();

    }

}
