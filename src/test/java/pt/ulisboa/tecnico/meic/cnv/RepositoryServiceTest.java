package pt.ulisboa.tecnico.meic.cnv;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by francisco on 08/05/2017.
 */
public class RepositoryServiceTest {
    private RepositoryService repositoryService;

    @Before
    public void setUp() throws Exception {
        repositoryService = new RepositoryService();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getMetrics() throws Exception {
        repositoryService.getMetrics(
                new Argument("test03.txt", 0, 0, 0,
                        0, 0, 0));
    }

}