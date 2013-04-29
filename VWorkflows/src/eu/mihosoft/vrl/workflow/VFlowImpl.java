/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.mihosoft.vrl.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/**
 *
 * @author Michael Hoffer <info@michaelhoffer.de>
 */
class VFlowImpl implements VFlow {

    ObjectProperty<VFlowModel> modelProperty = new SimpleObjectProperty<>();
    private ListChangeListener<VNode> nodesListener;
    private ListChangeListener<Connection> connectionsListener;
    private List<SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>> skinFactories = FXCollections.observableArrayList();
    private Map<SkinFactory, Map<String, VNodeSkin>> nodeSkins = new WeakHashMap<>();
    private Map<SkinFactory, Map<String, ConnectionSkin>> connectionSkins = new WeakHashMap<>();
    private ObservableMap<String, VFlow> subControllers = FXCollections.observableHashMap();
    private ChangeListener<Boolean> visibilityListener;
    private IdGenerator idGenerator;
    private NodeLookup nodeLookup;
    private FlowNodeSkinLookup nodeSkinLookup;

    public VFlowImpl(SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>... skinFactories) {
        setSkinFactories(skinFactories);

        init();
    }

    public VFlowImpl(Collection<SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>> skinFactories) {
        setSkinFactories(skinFactories);

        init();
    }

    private void init() {


        setIdGenerator(new IdGeneratorImpl());
        setNodeSkinLookup(new FlowNodeSkinLookupImpl(this));

        nodesListener = new ListChangeListener<VNode>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends VNode> change) {
                while (change.next()) {
                    if (change.wasPermutated()) {
                        for (int i = change.getFrom(); i < change.getTo(); ++i) {
                            //permutate
                        }
                    } else if (change.wasUpdated()) {
                        //update item
                    } else if (change.wasRemoved()) {
                        // removed
                        for (VNode n : change.getRemoved()) {
                            if (nodeSkins.containsKey(n.getId())) {
                                removeNodeSkinFromAllSkinFactories(n);
//                                 System.out.println("remove node: " + n.getId());
                            }

                            if (n instanceof FlowModel) {
                                subControllers.remove(n.getId());
                            }


                        }
                    } else if (change.wasAdded()) {
                        // added
                        for (VNode n : change.getAddedSubList()) {
                            if (!nodeSkins.containsKey(n.getId())) {
                                createNodeSkins(n);
//                                 System.out.println("add node: " + n.getId());
                            }
                        }
                    }

                } // end while change.next()
            }
        };

        connectionsListener = new ListChangeListener<Connection>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends Connection> change) {
                while (change.next()) {
                    if (change.wasPermutated()) {
                        for (int i = change.getFrom(); i < change.getTo(); ++i) {
                            //permutate
                        }
                    } else if (change.wasUpdated()) {
                        //update item
                    } else if (change.wasRemoved()) {
                        // removed
                        for (Connection n : change.getRemoved()) {
                            removeConnectionSkinFromAllSkinFactories(n);
//                            System.out.println("remove skin: " + n);
                        }
                    } else if (change.wasAdded()) {
                        // added
                        for (Connection c : change.getAddedSubList()) {
                            createConnectionSkins(c, c.getType());
                        }
                    }

                }
            }
        };


        visibilityListener = new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {

                System.out.println("visible: " + t1 + " : " + getModel().isVisible() + " : " + getModel().getId());

                if (t1) {
                    setSkinFactories(skinFactories);
                } else {
                    removeUI();
                }
            }
        };

        modelProperty.addListener(new ChangeListener<FlowModel>() {
            @Override
            public void changed(ObservableValue<? extends FlowModel> ov, FlowModel t, FlowModel t1) {

                removeUI();

                if (t != null) {

                    if (nodesListener != null) {
                        t.getNodes().removeListener(nodesListener);
                    }

                    if (connectionsListener != null) {
                        for (Connections conn : t.getAllConnections().values()) {
                            System.out.println("listener for conn-type removed: " + conn.getType());
                            conn.getConnections().removeListener(connectionsListener);
                        }
                    }

                    if (visibilityListener != null) {
                        t.visibleProperty().removeListener(visibilityListener);
                    }
                }

                if (t1 != null) {

                    _updateIdGenerator();
                    _updateNodeLookup();

                    if (nodesListener != null) {
                        t1.getNodes().addListener(nodesListener);
                    }

                    for (Connections conn : t1.getAllConnections().values()) {
                        conn.getConnections().addListener(connectionsListener);
                    }

                    t1.getAllConnections().addListener(new MapChangeListener<String, Connections>() {
                        @Override
                        public void onChanged(MapChangeListener.Change<? extends String, ? extends Connections> change) {
                            if (change.wasAdded()) {
                                change.getValueAdded().getConnections().addListener(connectionsListener);
                            }

                            if (change.wasRemoved()) {
                                change.getValueRemoved().getConnections().removeListener(connectionsListener);
                            }
                        }
                    });

                    getModel().visibleProperty().addListener(visibilityListener);
                }
            }
        });
    }

    private void _updateIdGenerator() {
        getModel().setIdGenerator(idGenerator);
    }

    private void _updateNodeLookup() {
        if (nodeLookup == null) {
            setNodeLookup(new NodeLookupImpl(getModel()));
        }

        getModel().setNodeLookup(getNodeLookup());
    }

    // TODO duplicated code
    private static String connectionId(String id, String s, String r) {
        return "id=" + id + ";[" + s + "]->[" + r + "]";
    }

    // TODO duplicated code
    private static String connectionId(Connection c) {
        return connectionId(c.getId(), c.getSenderId(), c.getReceiverId());
    }

    @Override
    public ConnectionResult tryConnect(VNode s, VNode r, String type) {
        return getModel().tryConnect(s, r, type);
    }

    @Override
    public ConnectionResult tryConnect(VFlow s, VNode r, String type) {
        return getModel().tryConnect(s.getModel(), r, type);
    }

    @Override
    public ConnectionResult tryConnect(VNode s, VFlow r, String type) {
        return getModel().tryConnect(s, r.getModel(), type);
    }

    @Override
    public ConnectionResult tryConnect(VFlow s, VFlow r, String type) {
        return getModel().tryConnect(s.getModel(), r.getModel(), type);
    }

    @Override
    public ConnectionResult connect(VNode s, VNode r, String type) {
        return getModel().connect(s, r, type);
    }

    @Override
    public ConnectionResult connect(VFlow s, VNode r, String type) {
        return getModel().connect(s.getModel(), r, type);
    }

    @Override
    public ConnectionResult connect(VNode s, VFlow r, String type) {
        return getModel().connect(s, r.getModel(), type);
    }

    @Override
    public ConnectionResult connect(VFlow s, VFlow r, String type) {
        return getModel().connect(s.getModel(), r.getModel(), type);
    }

    @Override
    public ObservableList<VNode> getNodes() {
        return getModel().getNodes();
    }

    @Override
    public VNode remove(VNode n) {
        return getModel().remove(n);
    }

    public ObservableMap<String, Connections> getAllConnections() {
        return getModel().getAllConnections();
    }

    @Override
    public Connections getConnections(String type) {
        return getModel().getConnections(type);
    }

    @Override
    public VNode getSender(Connection c) {
        return getModel().getSender(c);
    }

    @Override
    public VNode getReceiver(Connection c) {
        return getModel().getReceiver(c);
    }

    @Override
    public void setFlowNodeClass(Class<? extends VNode> cls) {
        getModel().setFlowNodeClass(cls);
    }

    @Override
    public Class<? extends VNode> getFlowNodeClass() {
        return getModel().getFlowNodeClass();
    }

    @Override
    public VNode newNode(ValueObject obj) {


        return getModel().newNode(obj);
    }

    @Override
    public VNode newNode() {
        return getModel().newNode();
    }

    private List<VNodeSkin<VNode>> createNodeSkins(VNode n) {

        List<VNodeSkin<VNode>> skins = new ArrayList<>();

        for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> skinFactory : skinFactories) {

            if (skinFactory == null) {
                return null;
            }

            if (getModel() != null && !getModel().isVisible()) {
                return null;
            }

            VNodeSkin skin = skinFactory.createSkin(n, this);

//        nodeSkins.put(n.getId(), skin);

            putNodeSkin(skinFactory, skin);



            skin.add();

            skins.add(skin);

        }

        return skins;
    }

    private void putNodeSkin(SkinFactory skinFactory, VNodeSkin<VNode> skin) {
        Map<String, VNodeSkin> nodeSkinMap = getNodeSkinMap(skinFactory);


        nodeSkinMap.put(skin.getModel().getId(), skin);
    }

    private VNodeSkin<VNode> getNodeSkin(SkinFactory skinFactory, String id) {
        Map<String, VNodeSkin> nodeSkinMap = getNodeSkinMap(skinFactory);

        return nodeSkinMap.get(id);
    }

    public List<VNodeSkin> getAllNodeSkins() {
        List<VNodeSkin> result = new ArrayList<>();

        for (SkinFactory<?, ?> skinFactory : skinFactories) {
            result.addAll(getNodeSkinMap(skinFactory).values());
        }

        return result;
    }

    private VNodeSkin<VNode> removeNodeSkinFromFactory(SkinFactory skinFactory, String id) {
        Map<String, VNodeSkin> nodeSkinMap = getNodeSkinMap(skinFactory);

        VNodeSkin skin = nodeSkinMap.remove(id);

        if (skin != null) {
            skin.remove();
        }

        return skin;
    }

    private void putConnectionSkin(SkinFactory skinFactory, ConnectionSkin<Connection> skin) {
        Map<String, ConnectionSkin> nodeSkinMap = getConnectionSkinMap(skinFactory);


        nodeSkinMap.put(skin.getModel().getId(), skin);
    }

    private ConnectionSkin<Connection> getConnectionSkin(SkinFactory skinFactory, String id) {
        Map<String, ConnectionSkin> nodeSkinMap = getConnectionSkinMap(skinFactory);

        return nodeSkinMap.get(id);
    }

    public List<ConnectionSkin> getAllConnectionSkins() {
        List<ConnectionSkin> result = new ArrayList<>();

        for (SkinFactory<?, ?> skinFactory : skinFactories) {
            result.addAll(getConnectionSkinMap(skinFactory).values());
        }

        return result;
    }

    private ConnectionSkin<Connection> removeConnectionSkinFromFactory(SkinFactory skinFactory, Connection c) {
        Map<String, ConnectionSkin> connectionSkinsMap = getConnectionSkinMap(skinFactory);

        ConnectionSkin skin = connectionSkinsMap.remove(connectionId(c));

        if (skin != null) {
            skin.remove();
        }

        return skin;
    }

    private List<ConnectionSkin> createConnectionSkins(Connection c, String type) {

        List<ConnectionSkin> skins = new ArrayList<>();

        for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> skinFactory : skinFactories) {

            if (skinFactory == null) {
                return null;
            }

            if (getModel() != null && !getModel().isVisible()) {
                return null;
            }

            ConnectionSkin skin = skinFactory.createSkin(c, this, type);

//            connectionSkins.put(connectionId(c), skin);
            putConnectionSkin(skinFactory, skin);

            skin.add();
            skins.add(skin);
        }

        return skins;
    }

    private void removeNodeSkinFromAllSkinFactories(VNode n) {

        for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> skinFactory : skinFactories) {

            if (skinFactory == null) {
                return;
            }

            removeNodeSkinFromFactory(skinFactory, n.getId());
        }

//        VNodeSkin skin = nodeSkins.remove(n.getId());
    }

//    private void removeConnectionSkin(SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> skinFactory, Connection c) {
//        
//           if (skinFactory == null) {
//            return;
//        }
//        
//        ConnectionSkin skin = getConnectionSkinMap(skinFactory).remove(connectionId(c));
//        
//        if (skin != null) {
//            skin.remove();
//        }
//    }
    private void removeConnectionSkinFromAllSkinFactories(Connection c) {

        for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> skinFactory : this.skinFactories) {
            removeConnectionSkinFromFactory(skinFactory, c);
        }
    }

    private void removeUI() {

//        for(SkinFactory<?,?> skinFactory : skinFactories) {

        List<VNodeSkin> nodeDelList = getAllNodeSkins();

        for (VNodeSkin<VNode> nS : nodeDelList) {
            nS.remove();
        }

        nodeSkins.clear();

        List<ConnectionSkin> connectionDelList = getAllConnectionSkins();

        for (ConnectionSkin<Connection> cS : connectionDelList) {
            cS.remove();
        }

        connectionSkins.clear();


    }

    @Override
    public final void setSkinFactories(Collection<SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>> skinFactories) {
        SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>[] skinFactoryArray = new SkinFactory[skinFactories.size()];

        setSkinFactories(skinFactoryArray);
    }

    /**
     * @param nodeSkinFactory the nodeSkinFactory to set
     */
    @Override
    public final void setSkinFactories(SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>... skinFactory) {

        this.skinFactories.clear();
        this.skinFactories.addAll(Arrays.asList(skinFactory));


        if (skinFactory == null) {
            removeUI();

        } else {

            for (VNode n : getNodes()) {
                createNodeSkins(n);
            }

            for (Connections cns : getAllConnections().values()) {
                for (Connection c : cns.getConnections()) {

                    createConnectionSkins(c, c.getType());

                }
            }
        }

        if (getModel() != null && !getModel().isVisible()) {
            return;
        }

        for (VFlow fC : subControllers.values()) {

            Collection<SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>> childSkinFactories = new ArrayList<>();

            for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> sF : skinFactories) {

                SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> childNodeSkinFactory = null;

                if (sF != null) {
                    childNodeSkinFactory = sF.createChild(
                            getNodeSkin(sF, fC.getModel().getId()));
                }

                childSkinFactories.add(childNodeSkinFactory);


            }

            fC.setSkinFactories(childSkinFactories);
        }
    }

    @Override
    public void setNodeSkinLookup(FlowNodeSkinLookup skinLookup) {
        this.nodeSkinLookup = skinLookup;
    }

    @Override
    public FlowNodeSkinLookup getNodeSkinLookup() {
        return this.nodeSkinLookup;
    }

    @Override
    public void addConnections(Connections connections, String flowType) {
        getModel().addConnections(connections, flowType);
    }

    @Override
    public void setModel(VFlowModel flow) {
        modelProperty.set(flow);

        for (VNode n : flow.getNodes()) {
            if (n instanceof VFlowModel) {
                newSubFlow((VFlowModel) n);
            }
        }

    }

    @Override
    public VFlowModel getModel() {
        return modelProperty.get();
    }

    @Override
    public ObjectProperty modelProperty() {
        return modelProperty;
    }

    private VFlow newSubFlow(VFlowModel flowNode) {

        Collection<SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin>> childFactories = new ArrayList<>();

        for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> skinFactory : skinFactories) {

            VNodeSkin<VNode> skin = getNodeSkin(skinFactory, flowNode.getId());//nodeSkins.get(flowNode.getId());

            SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> childFactory = null;

            if (skinFactory != null) {
                childFactory = skinFactory.createChild(skin);
                childFactories.add(childFactory);
            }

        }

        VFlow controller = new VFlowImpl(childFactories);

        controller.setIdGenerator(getIdGenerator());
        controller.setNodeLookup(getNodeLookup());
        controller.setNodeSkinLookup(getNodeSkinLookup());
        controller.setModel(flowNode);


        for (String connectionType : getAllConnections().keySet()) {
            if (flowNode.getConnections(connectionType) == null) {
                controller.addConnections(
                        VConnections.newConnections(connectionType),
                        connectionType);
            }
        }

        subControllers.put(flowNode.getId(), controller);

        return controller;
    }

    @Override
    public VFlow newSubFlow(ValueObject obj) {
        return newSubFlow(getModel().newFlowNode(obj));
    }

    @Override
    public VFlow newSubFlow() {
        VFlowModel flowNode = getModel().newFlowNode();

        return newSubFlow(flowNode);
    }

    @Override
    public Collection<VFlow> getSubControllers() {
        return Collections.unmodifiableCollection(subControllers.values());
    }

    @Override
    public void setIdGenerator(IdGenerator generator) {
        this.idGenerator = generator;
    }

    @Override
    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    /**
     * @return the nodeLookup
     */
    @Override
    public NodeLookup getNodeLookup() {
        return nodeLookup;
    }

    /**
     * @param nodeLookup the nodeLookup to set
     */
    @Override
    public void setNodeLookup(NodeLookup nodeLookup) {
        this.nodeLookup = nodeLookup;
    }

    @Override
    public List<VNodeSkin> getNodeSkinsById(String id) {
        List<VNodeSkin> result = new ArrayList<>();

        for (SkinFactory<? extends ConnectionSkin, ? extends VNodeSkin> sF : this.skinFactories) {
            result.add(getNodeSkin(sF, id));
        }

        return result;
    }

    @Override
    public void setVisible(boolean state) {
        getModel().setVisible(state);
    }

    @Override
    public boolean isVisible() {
        return getModel().isVisible();
    }

    @Override
    public BooleanProperty visibleState() {
        return getModel().visibleProperty();
    }

    @Override
    public boolean isInputOfType(String type) {
        return getModel().isInputOfType(type);
    }

    @Override
    public boolean isOutputOfType(String type) {
        return getModel().isOutputOfType(type);
    }

    @Override
    public boolean isInput() {
        return getModel().isInput();
    }

    @Override
    public boolean isOutput() {
        return getModel().isOutput();
    }

    @Override
    public void setInput(boolean state, String type) {
        getModel().setInput(state, type);
    }

    @Override
    public void setOutput(boolean state, String type) {
        getModel().setOutput(state, type);
    }

    @Override
    public ObservableList<String> getInputTypes() {
        return getModel().getInputTypes();
    }

    @Override
    public ObservableList<String> getOutputTypes() {
        return getModel().getOutputTypes();
    }

    private Map<String, VNodeSkin> getNodeSkinMap(SkinFactory skinFactory) {
        Map<String, VNodeSkin> nodeSkinMap = nodeSkins.get(skinFactory);
        if (nodeSkinMap == null) {
            nodeSkinMap = new HashMap<>();
        }
        return nodeSkinMap;
    }

    private Map<String, ConnectionSkin> getConnectionSkinMap(SkinFactory skinFactory) {
        Map<String, ConnectionSkin> connectionSkinMap = connectionSkins.get(skinFactory);
        if (connectionSkinMap == null) {
            connectionSkinMap = new HashMap<>();
        }
        return connectionSkinMap;
    }
}