package br.uefs.larsid.dlt.iot.soft.models;

import br.uefs.larsid.dlt.iot.soft.entities.Sensor;
import br.uefs.larsid.dlt.iot.soft.mqtt.ListenerConnection;
import br.uefs.larsid.dlt.iot.soft.mqtt.ListenerResponse;
import br.uefs.larsid.dlt.iot.soft.mqtt.MQTTClient;
import br.uefs.larsid.dlt.iot.soft.services.Controller;
import br.uefs.larsid.dlt.iot.soft.utils.MapToArray;
import br.uefs.larsid.dlt.iot.soft.utils.SortTopK;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public class ControllerImpl implements Controller {

  /*-------------------------Constantes---------------------------------------*/
  private static final int QOS = 1;
  private static final String TOP_K = "GET topk";
  private static final String TOP_K_RES_FOG = "TOP_K_HEALTH_FOG_RES/";
  private static final String TOP_K_RES = "TOP_K_HEALTH_RES/#";
  private static final String INVALID_TOP_K = "INVALID_TOP_K/#";
  private static final String INVALID_TOP_K_FOG = "INVALID_TOP_K_FOG/";


  private static final String AUTHENTICATED_DEVICES = "AUTHENTICATED_DEVICES";

  private static final String CONNECT = "SYN";
  private static final String DISCONNECT = "FIN";
  /*--------------------------------------------------------------------------*/

  private Node node;

  private MQTTClient MQTTClientUp;
  private MQTTClient MQTTClientHost;

  private Map<String, Map<String, Integer>> topKScores = new LinkedHashMap<String, Map<String, Integer>>();
  private Map<String, Integer> responseQueue = new LinkedHashMap<String, Integer>();
  private Map<String, Integer> devicesScores;

  private List<String> nodesUris;
  private int timeoutInSeconds;

  private JsonObject sensorsTypesJSON = new JsonObject();

  private long responseTime;
  private boolean debugModeValue;
  private static final Logger logger = Logger.getLogger(ControllerImpl.class.getName());

  public ControllerImpl() {
  }

  /**
   * Inicializa o bundle.
   */
  public void start() {
    this.MQTTClientUp.connect();
    this.MQTTClientHost.connect();
    this.node.setController(this);

    devicesScores = new LinkedHashMap<String, Integer>();
    nodesUris = new ArrayList<>();
    String[] topicsConnection = { CONNECT, DISCONNECT };
    String[] topicsResponse = { TOP_K_RES, INVALID_TOP_K };

    new ListenerConnection(
        this,
        MQTTClientHost,
        topicsConnection,
        QOS,
        debugModeValue);
    new ListenerResponse(
        this,
        MQTTClientHost,
        topicsResponse,
        QOS,
        debugModeValue);
  }

  /**
   * Finaliza o bundle.
   */
  public void stop() {
    this.MQTTClientUp.unsubscribe(TOP_K);
    this.MQTTClientUp.unsubscribe(CONNECT);
    this.MQTTClientUp.unsubscribe(DISCONNECT);
    this.MQTTClientHost.unsubscribe(TOP_K_RES);
    this.MQTTClientHost.unsubscribe(INVALID_TOP_K);
    this.MQTTClientHost.unsubscribe(AUTHENTICATED_DEVICES);

    this.MQTTClientHost.disconnect();
    this.MQTTClientUp.disconnect();
  }

  public void sendTopK() {

    // Gera o JSON de requisição do Top-K aleatório

    JsonObject jsonTopK = new JsonObject();

    String id = String.format("request-%s", System.currentTimeMillis());
    int k = new Random().nextInt(10) + 1;

    JsonArray functionHealth = new JsonArray();
    functionHealth.add(createSensorJsonObject("RespirationRateSensor", 2));
    functionHealth.add(createSensorJsonObject("PulseOxymeterSensor", 2));
    functionHealth.add(createSensorJsonObject("BodyThermometerSensor", 2));
    functionHealth.add(createSensorJsonObject("HeartRateSensor", 2));
    
    jsonTopK.addProperty("id", id);
    jsonTopK.addProperty("k", k);
    jsonTopK.add("functionHealth", functionHealth);

    // Envia solicitação de Top-K via MQTT

    this.MQTTClientHost.publish(TOP_K, jsonTopK.toString().getBytes(), QOS);

    // Inicia o cronômetro para a verificação do tempo de resposta

    this.setResponseTime(System.currentTimeMillis());

    // Aguarda recebimento de todos os Top-Ks, calcula o Top-K geral e 
    // devolve a resposta para o cliente.

    Map<String, Integer> scoreMapEmpty = new LinkedHashMap<String, Integer>();
    this.getTopKScores().put(id, scoreMapEmpty);
    this.addResponse(id);

    this.publishTopK(id, k, functionHealth);
  }

  private static JsonObject createSensorJsonObject(String sensor, int weight) {
    JsonObject sensorJsonObject = new JsonObject();
    sensorJsonObject.addProperty("sensor", sensor);
    sensorJsonObject.addProperty("weight", weight);
    return sensorJsonObject;
  } 

  /**
   * Publica o Top-K calculado para a camada de cima.
   *
   * @param id             String - Id da requisição.
   * @param k              int - Quantidade de scores requisitados.
   * @param functionHealth JsonArray - Array contendo a função de cálculo do
   *                       Top-K.
   */
  @Override
  public void publishTopK(String id, int k, JsonArray functionHealth) {
    printlnDebug("Waiting for Gateway nodes to send their Top-K");

    long start = System.currentTimeMillis();
    long end = start + this.timeoutInSeconds * 1000;

    while (this.responseQueue.get(id) < this.nodesUris.size() &&
        System.currentTimeMillis() < end) {
    }

    this.calculateGeneralTopK(id, k);
  }

  /**
   * Publica os tipos de sensores para a camada de cima.
   */
  // @Override
  // public void publishSensorType() {
  //   printlnDebug("Waiting for Gateway nodes to send their sensors types");

  //   long start = System.currentTimeMillis();
  //   long end = start + this.timeoutInSeconds * 1000;

  //   /*
  //    * Enquanto a quantidade de respostas da requisição for menor que o número
  //    * de nós filhos
  //    */
  //   while (this.responseQueue.get("getSensors") < this.nodesUris.size() &&
  //       System.currentTimeMillis() < end) {
  //   }

  //   byte[] payload = sensorsTypesJSON.toString().replace("\\", "").getBytes();

  //   MQTTClientUp.publish(SENSORS_FOG_RES, payload, 1);

  //   this.removeSpecificResponse("getSensors");
  // }

  /**
   * Requisita os tipos de sensores de um dispositivo conectado.
   *
   * @return List<String>
   */
  public List<String> loadSensorsTypes() {
    List<String> sensorsList = new ArrayList<>();

    for (Sensor sensor : this.node.getDevices().get(0).getSensors()) {
      sensorsList.add(sensor.getType());
    }

    return sensorsList;
  }

  /**
   * Retorna o mapa de requisições do sistema, composto pelo
   * id da requisição (chave) e o mapa de scores (valor).
   * O mapa de scores é composto pelo nome do dispositivo (Chave)
   * e o score (valor) associado.
   *
   * @return Map
   */
  @Override
  public Map<String, Map<String, Integer>> getTopKScores() {
    return this.topKScores;
  }

  /**
   * Retorna o mapa de scores de acordo com o id da requisição
   * passado por parâmetro.
   *
   * @param id String - Id da requisição.
   * @return Map
   */
  @Override
  public Map<String, Integer> getMapById(String id) {
    return this.topKScores.get(id);
  }

  /**
   * Adiciona um mapa de scores de uma nova requisição no mapa de
   * requisições na sua respectiva.
   *
   * @param id     String - Id da requisição.
   * @param fogMap Map - Mapa de requisições.
   */
  @Override
  public void putScores(String id, Map<String, Integer> fogMap) {
    if (this.topKScores.get(id) != null) {
      this.topKScores.get(id).putAll(fogMap);
    } else {
      this.topKScores.put(id, fogMap).isEmpty();
    }
  }

  /**
   * Adiciona os sensores em um JSON para enviar para a camada superior.
   *
   * @param jsonReceived JSONObject - JSON contendo os tipos dos sensores.
   */
  @Override
  public void putSensorsTypes(JsonObject jsonReceived) {
    if (this.sensorsTypesJSON.get("sensors").getAsString().equals("[]")) {
      sensorsTypesJSON = jsonReceived;
    }
  }

  /**
   * Envia uma mensagem indicando que o Top-K pedido possui uma quantidade
   * inválida.
   *
   * @param topicId String - Id da requisição do Top-K.
   * @param message String - Mensagem.
   */
  @Override
  public void sendInvalidTopKMessage(String topicId, String message) {
    printlnDebug(message);

    MQTTClientUp.publish(INVALID_TOP_K_FOG + topicId, message.getBytes(), QOS);
  }

  /**
   * Remove do mapa de requisições o id da requisição junto com mapa de scores
   * associado a ele.
   *
   * @param id String - Id da requisição.
   */
  @Override
  public void removeRequest(String id) {
    this.topKScores.remove(id);
  }

  /**
   * Cria uma nova chave no mapa de resposta dos filhos.
   *
   * @param id String - Id da requisição.
   */
  @Override
  public void addResponse(String id) {
    responseQueue.put(id, 0);
  }

  /**
   * Atualiza a quantidade de respostas.
   *
   * @param id String - Id da requisição.
   */
  @Override
  public void updateResponse(String id) {
    int temp = responseQueue.get(id);
    responseQueue.put(id, ++temp);
  }

  /**
   * Remove uma resposta específica da fila de respostas.
   *
   * @param id String - Id da requisição.
   */
  @Override
  public void removeSpecificResponse(String id) {
    responseQueue.remove(id);
  }

  /**
   * Envia um mapa vazio.
   *
   * @param topicId String - Id da requisição.
   */
  @Override
  public void sendEmptyTopK(String topicId) {
    byte[] payload = new LinkedHashMap<String, Map<String, Integer>>()
        .toString()
        .getBytes();

    this.MQTTClientUp.publish(TOP_K_RES_FOG + topicId, payload, QOS);
  }

  /**
   * Calcula o Top-k dos Top-ks de acordo com o valor de k solicitado.
   */
  public void calculateGeneralTopK(String id, int k) {
    Map<String, Integer> topK = new LinkedHashMap<String, Integer>();
    Map<String, Integer> topKReal = new LinkedHashMap<String, Integer>();
    String deviceListJson;

    printlnDebug("OK... now let's calculate the TOP-K of TOP-K's!");

    if (this.topKScores.get(id) != null) {
      /*
       * Reordenando o mapa de Top-K (Ex: {device2=23, device1=14}) e
       * atribuindo-o à carga de mensagem do MQTT
       */
      topK = SortTopK.sortTopK(this.getMapById(id), k, debugModeValue);
  
      if (this.node.hasCollectRealScoreService()) {
  
        for (String deviceId : topK.keySet()) {
          if (this.devicesScores.containsKey(deviceId)) {
            topKReal.put(deviceId, this.devicesScores.get(deviceId));
          }
        }
  
        this.devicesScores.clear();
      }
  
      printlnDebug("==== Cloud gateway -> Client  ====");
  
      JsonObject json = new JsonObject();
      json.addProperty("id", id);
      json.addProperty("timestamp", System.currentTimeMillis());
  
      if (this.node.hasCollectRealScoreService()) {
        deviceListJson = new Gson().toJson(MapToArray.mapToArray(topK, topKReal));
      } else {
        deviceListJson = new Gson().toJson(MapToArray.mapToArray(topK));
      }
  
      json.addProperty("devices", deviceListJson);
  
      byte[] payload = json.toString().replace("\\", "").getBytes();
  
      MQTTClientUp.publish(TOP_K_RES_FOG + id, payload, 1);
  
      this.removeRequest(id);
      this.removeSpecificResponse(id);
      
    } else {
      printlnDebug("==== Cloud gateway -> Client  ====");

      byte[] payload = "There are no devices connected".getBytes();
  
      MQTTClientUp.publish(TOP_K_RES_FOG + id, payload, 1);
    }

    this.showResponseTime();
  }

  /**
   * Exibe o tempo de resposta de uma solicitação.
   */
  private void showResponseTime() {
    this.responseTime = System.currentTimeMillis() - this.responseTime;

    printlnDebug("+------------------------------+");
    printlnDebug(
        String.format("Tempo de resposta: %s ms", this.responseTime));
    printlnDebug("+------------------------------+\n");
  }

  public Node getNode() {
    return node;
  }

  public void setNode(Node node) {
    this.node = node;
  }

  /**
   * Adiciona um URI na lista de URIs.
   *
   * @param uri String - URI que deseja adicionar.
   */
  @Override
  public void addNodeUri(String uri) {
    if (!this.nodesUris.contains(uri)) {
      this.nodesUris.add(uri);
    }

    printlnDebug(String.format("URI: %s added in the nodesIps list.", uri));
    this.showNodesConnected();
  }

  /**
   * Remove uma URI na lista de URIs.
   *
   * @param uri String - URI que deseja remover.
   */
  @Override
  public void removeNodeUri(String uri) {
    int pos = this.findNodeUri(uri);

    if (pos != -1) {
      this.nodesUris.remove(pos);

      printlnDebug(
          String.format("URI: %s removed in the nodesIps list.", uri));

      this.showNodesConnected();
    } else {
      printlnDebug("Error, the desired node was not found.");
    }
  }

  /**
   * Retorna a posição de um URI na lista de URIs
   *
   * @param uri String - URI que deseja a posição.
   * @return int
   */
  private int findNodeUri(String uri) {
    for (int pos = 0; pos < this.nodesUris.size(); pos++) {
      if (this.nodesUris.get(pos).equals(uri)) {
        return pos;
      }
    }

    return -1;
  }

  /**
   * Retorna a lista de URIs dos nós conectados.
   *
   * @return List
   */
  @Override
  public List<String> getNodeUriList() {
    return this.nodesUris;
  }

  /**
   * Retorna a quantidade de nós conectados.
   *
   * @return String
   */
  @Override
  public int getNodes() {
    return this.nodesUris.size();
  }

  /**
   * Exibe a URI dos nós que estão conectados.
   */
  public void showNodesConnected() {
    printlnDebug("+---- Nodes URI Connected ----+");
    for (String nodeIp : this.getNodeUriList()) {
      printlnDebug("     " + nodeIp);
    }

    if (this.getNodeUriList().size() == 0) {
      printlnDebug("        empty");
    }
    printlnDebug("+----------------------------+");
  }

  private void printlnDebug(String str) {
    if (debugModeValue) {
      logger.info(str);
    }
  }

  public boolean isDebugModeValue() {
    return this.debugModeValue;
  }

  public void setDebugModeValue(boolean debugModeValue) {
    this.debugModeValue = debugModeValue;
  }

  public MQTTClient getMQTTClientUp() {
    return this.MQTTClientUp;
  }

  public void setMQTTClientUp(MQTTClient MQTTClientUp) {
    this.MQTTClientUp = MQTTClientUp;
  }

  public void setTopKScores(Map<String, Map<String, Integer>> topKScores) {
    this.topKScores = topKScores;
  }

  public MQTTClient getMQTTClientHost() {
    return this.MQTTClientHost;
  }

  public void setMQTTClientHost(MQTTClient mQTTClientHost) {
    this.MQTTClientHost = mQTTClientHost;
  }

  public List<String> getNodesUris() {
    return nodesUris;
  }

  public void setNodesUris(List<String> nodesUris) {
    this.nodesUris = nodesUris;
  }

  public void setTimeoutInSeconds(int timeoutInSeconds) {
    this.timeoutInSeconds = timeoutInSeconds;
  }

  public long getResponseTime() {
    return this.responseTime;
  }

  public void setResponseTime(long responseTime) {
    this.responseTime = responseTime;
  }

  /**
   * Retorna um JSON contendo os tipos de sensores disponíveis.
   *
   * @return JsonObject
   */
  @Override
  public JsonObject getSensorsTypesJSON() {
    return sensorsTypesJSON;
  }

  public Map<String, Integer> getDevicesScores() {
    return this.devicesScores;
  }

  public void addDeviceScore(String deviceId, int score) {
    this.devicesScores.put(deviceId, score);
  }

  public void putDevicesScoresAll(Map<String, Integer> devicesScores) {
    this.devicesScores.putAll(devicesScores);
  }
}
