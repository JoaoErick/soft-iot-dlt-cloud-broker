package br.uefs.larsid.dlt.iot.soft.services;

import java.util.List;

import br.uefs.larsid.dlt.iot.soft.entities.Device;

public interface INode {
    /**
     * Adiciona os dispositivos que foram requisitados na lista de dispositivos.
     *
     * @param strDevices String - Dispositivos requisitados.
     */
    public void loadConnectedDevices();

    /**
     * Obtém a lista de dispositivos virtuais.
     *
     * @return List<String>
     */
    public List<Device> getDevices();

    public void setDevices(List<Device> devices);

    // public List<String> getAuthenticatedDevicesIds();

    // public void setAuthenticatedDevicesIds(List<String> authenticatedDevicesIds);

    /**
     * Obtém o valor do intervalo definido para enviar solicitações de Top-K.
     *
     * @return int
     */
    public int getSendTopKRequestTaskTime();

    public void setSendTopKRequestTaskTime(int sendTopKRequestTaskTime);

    /**
     * Obtém o endereço da API dos dispositivos virtuais.
     *
     * @return String
     */
    public String getDeviceAPIAddress();

    public void setDeviceAPIAddress(String deviceAPIAddress);

    /**
     * Verifica se o gateway deve coletar os scores reais dos dispositivos.
     * 
     * @return boolean
     */
    public boolean hasCollectRealScoreService();

    public void setHasCollectRealScoreService(boolean hasCollectRealScoreService);

    public Controller getController();

    public void setController(Controller controller);

    public boolean isDebugModeValue();

    public void setDebugModeValue(boolean debugModeValue);
}
