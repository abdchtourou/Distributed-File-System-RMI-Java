package interfaces;

import model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface CoordinatorInterface extends Remote {
    User getUserInfo(String username) throws RemoteException;
    String login(String username, String password) throws RemoteException;
    boolean registerUser(String username, String password, String department, String role) throws RemoteException;
    boolean setPermissions(String managerToken, String username, List<String> permissions) throws RemoteException;

    boolean uploadFile(String token, String department, String fileName, byte[] fileData) throws RemoteException;
    boolean deleteFile(String token, String department, String fileName) throws RemoteException;
    boolean updateFile(String token, String department, String fileName, byte[] newData) throws RemoteException;

    byte[] viewFile(String token, String targetDepartment, String fileName) throws RemoteException;
    List<String> listFiles(String token, String targetDepartment) throws RemoteException;
    List<String> getUserPermissions(String requesterToken, String targetUsername) throws RemoteException;

}