package ms.maomer.neobackup;

import java.nio.file.Path;

public interface ServerBridge {
    Path worldsRootPath();

    void saveAll();

    void executeOnMainThread(Runnable task);
}
