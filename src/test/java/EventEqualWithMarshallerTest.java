/*
 * Copyright 2020 InfAI (CC SES)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.sun.net.httpserver.HttpServer;
import org.infai.ses.senergy.models.DeviceMessageModel;
import org.infai.ses.senergy.models.MessageModel;
import org.infai.ses.senergy.operators.Config;
import org.infai.ses.senergy.operators.Helper;
import org.infai.ses.senergy.operators.Message;
import org.infai.ses.senergy.testing.utils.JSONHelper;
import org.infai.ses.senergy.utils.ConfigProvider;
import org.json.JSONException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;


public class EventEqualWithMarshallerTest {
    public static boolean called = false;
    private static Object processVariable = null;

    private Object jsonNormalize(Object in) throws ParseException {
        Map<String, Object> wrapper = new HashMap<String, Object>();
        wrapper.put("value", in);
        JSONObject temp = new JSONObject(wrapper);
        Object candidate = ((JSONObject)(new JSONParser().parse(temp.toJSONString()))).get("value");
        if(candidate instanceof Long){
            candidate = Double.valueOf((Long)candidate);
        }
        return candidate;
    }

    private void testWithConversion(String configuredValue, Object actualValue, String conversionResp, boolean expectedToTrigger) throws IOException, JSONException {
        EventEqualWithMarshallerTest.called = false;
        HttpServer server = TriggerServerMock.create(inputStream -> {
            JSONParser jsonParser = new JSONParser();
            try {
                JSONObject jsonObject = (JSONObject)jsonParser.parse(new InputStreamReader(inputStream, "UTF-8"));
                if(
                        jsonObject.containsKey("processVariablesLocal")
                                && ((JSONObject)jsonObject.get("processVariablesLocal")).containsKey("event")
                                && ((JSONObject)((JSONObject)jsonObject.get("processVariablesLocal")).get("event")).containsKey("value")
                ){
                    EventEqualWithMarshallerTest.called = true;
                    EventEqualWithMarshallerTest.processVariable = ((JSONObject)((JSONObject)jsonObject.get("processVariablesLocal")).get("event")).get("value");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        HttpServer converterServer = ConverterServerMock.createWithResponse("/v2/unmarshal/service-id", conversionResp);
        String mockUrl = "http://localhost:"+converterServer.getAddress().getPort();
        ConverterInterface converter = new Marshaller(mockUrl, "userToken", "function-id", "aspect-id", "", "outputCharacteristic", "{\"test\":\"service-id\"}");
        EventEqual events = new EventEqual("", configuredValue, "http://localhost:"+server.getAddress().getPort()+"/endpoint", "test", converter);
        Config config = new Config(new JSONHelper().parseFile("config.json").toString());
        ConfigProvider.setConfig(config);
        MessageModel model = new MessageModel();
        Message message = new Message();
        events.configMessage(message);
        JSONObject m = new JSONHelper().parseFile("message.json");
        ((JSONObject)((JSONObject) m.get("value")).get("reading")).put("value", actualValue);
        DeviceMessageModel deviceMessageModel = JSONHelper.getObjectFromJSONString(m.toString(), DeviceMessageModel.class);
        assert deviceMessageModel != null;
        String topicName = config.getInputTopicsConfigs().get(0).getName();
        model.putMessage(topicName, Helper.deviceToInputMessageModel(deviceMessageModel, topicName));
        message.setMessage(model);
        events.run(message);
        server.stop(0);
        Assert.assertEquals(EventEqualWithMarshallerTest.called, expectedToTrigger);
        if(expectedToTrigger){
            try {
                Object a = jsonNormalize(EventEqualWithMarshallerTest.processVariable);
                Object b = jsonNormalize(actualValue);
                Assert.assertEquals(a, b);
            } catch (ParseException e) {
                Assert.fail(e.getMessage());
            }
        }
    }


    @Test
    public void convertedStringEqualTrue() throws IOException, JSONException {
        testWithConversion("\"foobar\"", "foobar", "\"foobar\"",true);
    }

    @Test
    public void convertedStringEqualFalse() throws IOException, JSONException {
        testWithConversion("\"foobar\"", "foobar", "foobar",false);
    }


    @Test
    public void convertedNumberEqualTrue() throws IOException, JSONException {
        testWithConversion("42", 42, "42",true);
    }

    @Test
    public void convertedNumberEqualFalse() throws IOException, JSONException {
        testWithConversion("42", 42, "13",false);
    }

    @Test
    public void convertedNumberEqualTrue2() throws IOException, JSONException {
        testWithConversion("42", 42, "42",true);
    }

    @Test
    public void convertedNumberEqualFalse2() throws IOException, JSONException {
        testWithConversion("42", 13, "13",false);
    }

}
