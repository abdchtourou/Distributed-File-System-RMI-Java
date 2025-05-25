package interfaces;// NodeInterface.java
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NodeInterface extends Remote {
    boolean storeFile(String department, String fileName, byte[] data) throws RemoteException;
    byte[] retrieveFile(String department, String fileName) throws RemoteException;
    boolean deleteFile(String department, String fileName) throws RemoteException;
    boolean synchronize() throws RemoteException;
    boolean isAlive() throws RemoteException;
    // ✅ الإضافة الجديدة:
    List<String> listFiles(String department) throws RemoteException;
}