package coordinator;

import node.CoordinatorImpl;
import interfaces.NodeInterface;
import node.NodeImpl;
import sync.SocketSyncManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CoordinatorServer {
    private static SocketSyncManager socketServer1;
    private static SocketSyncManager socketServer2;
    private static SocketSyncManager socketServer3;
    
    public static void main(String[] args) {
        try {
            // 1. إنشاء الكائن الفعلي (Implementation)
            CoordinatorImpl coordinator = new CoordinatorImpl();

            // 2. تسجيل العقد (ثلاث عقد على نفس الجهاز لأغراض الاختبار)
            NodeInterface node1 = new NodeImpl("Node1", "storage1");
            NodeInterface node2 = new NodeImpl("Node2", "storage2");
            NodeInterface node3 = new NodeImpl("Node3", "storage3");

            coordinator.registerNode(node1);
            coordinator.registerNode(node2);
            coordinator.registerNode(node3);

            // 3. تشغيل خوادم السوكيت للعقد مع المزامنة التلقائية المدمجة
            socketServer1 = new SocketSyncManager("Node1", "storage1", 8081);
            socketServer2 = new SocketSyncManager("Node2", "storage2", 8082);
            socketServer3 = new SocketSyncManager("Node3", "storage3", 8083);
            
            socketServer1.startServer();
            socketServer2.startServer();
            socketServer3.startServer();

            // 4. تسجيل الـ Coordinator في الـ RMI Registry
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("Coordinator", coordinator);

            System.out.println("✅ Coordinator server is running...");
            System.out.println("🔌 Socket servers running on ports 8081, 8082, and 8083");
            System.out.println("📁 Storage nodes: storage1, storage2, storage3");
            System.out.println("🕐 Automatic daily synchronization enabled at 23:30 for all nodes");
            System.out.println("🔄 Each node will automatically sync with others every night");
            
            // إضافة shutdown hook لإيقاف خوادم السوكيت بشكل صحيح
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🔌 Stopping socket servers...");
                if (socketServer1 != null) socketServer1.stop();
                if (socketServer2 != null) socketServer2.stop();
                if (socketServer3 != null) socketServer3.stop();
            }));

        } catch (Exception e) {
            System.err.println("❌ Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
