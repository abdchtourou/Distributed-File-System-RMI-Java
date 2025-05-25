//package node;
//
//import java.rmi.Naming;
//import java.rmi.registry.LocateRegistry;
//
//public class DevelopmentNodeServer {
//    public static void main(String[] args) {
//        try {
//            LocateRegistry.createRegistry(1099);
//            DevelopmentNode node = new DevelopmentNode();
//            Naming.rebind("rmi://localhost:1099/DevelopmentNode", node);
//            System.out.println("Development Node is running...");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
