package coordinator;

import node.CoordinatorImpl;
import interfaces.NodeInterface;
import node.NodeImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CoordinatorServer {
    public static void main(String[] args) {
        try {
            // 1. إنشاء الكائن الفعلي (Implementation)
            CoordinatorImpl coordinator = new CoordinatorImpl();

            // 2. تسجيل العقد (مثال: عقدتين على نفس الجهاز لأغراض الاختبار)
            NodeInterface node1 = new NodeImpl("Node1", "storage1");
            NodeInterface node2 = new NodeImpl("Node2", "storage2");

            coordinator.registerNode(node1);
//            coordinator.registerNode(node2);

            // 3. تسجيل الـ Coordinator في الـ RMI Registry
            Registry registry = LocateRegistry.createRegistry(1099); // المنفذ الافتراضي
            registry.rebind("Coordinator", coordinator);

            System.out.println("✅ Coordinator server is running...");

        } catch (Exception e) {
            System.err.println("❌ Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
