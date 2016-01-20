package org.rakam;

import com.google.common.eventbus.EventBus;
import org.rakam.analysis.EventExplorer;
import org.rakam.analysis.InMemoryQueryMetadataStore;
import org.rakam.analysis.JDBCMetastore;
import org.rakam.analysis.JDBCPoolDataSource;
import org.rakam.analysis.PrestoMaterializedViewService;
import org.rakam.analysis.TestEventExplorer;
import org.rakam.collection.event.FieldDependencyBuilder;
import org.rakam.collection.event.metastore.Metastore;
import org.rakam.event.TestingEnvironment;
import org.rakam.plugin.EventStore;
import org.rakam.plugin.JDBCConfig;
import org.rakam.report.PrestoConfig;
import org.rakam.report.PrestoContinuousQueryService;
import org.rakam.report.PrestoEventExplorer;
import org.rakam.report.PrestoQueryExecutor;
import org.rakam.report.QueryExecutorService;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.time.Clock;

public class PrestoTestEventExplorer extends TestEventExplorer {

    private EventExplorer eventExplorer;
    private TestingPrestoEventStore testingPrestoEventStore;
    private TestingEnvironment testingEnvironment;
    private JDBCMetastore metastore;

    @BeforeSuite
    @Override
    public void setUp() throws Exception {
        testingEnvironment = new TestingEnvironment();
        PrestoConfig prestoConfig = testingEnvironment.getPrestoConfig();
        JDBCConfig postgresqlConfig = testingEnvironment.getPostgresqlConfig();

        JDBCPoolDataSource metastoreDataSource = JDBCPoolDataSource.getOrCreateDataSource(postgresqlConfig);
        InMemoryQueryMetadataStore inMemoryQueryMetadataStore = new InMemoryQueryMetadataStore();

        EventBus eventBus = new EventBus();

        metastore = new JDBCMetastore(metastoreDataSource, prestoConfig,
                eventBus, new FieldDependencyBuilder().build());
        metastore.setup();

        PrestoQueryExecutor prestoQueryExecutor = new PrestoQueryExecutor(prestoConfig, metastore);

        PrestoContinuousQueryService continuousQueryService = new PrestoContinuousQueryService(inMemoryQueryMetadataStore,
                prestoQueryExecutor, prestoConfig);
        eventBus.register(new EventExplorerListener(continuousQueryService));

        QueryExecutorService queryExecutorService = new QueryExecutorService(prestoQueryExecutor, inMemoryQueryMetadataStore, metastore,
                new PrestoMaterializedViewService(prestoQueryExecutor, inMemoryQueryMetadataStore, Clock.systemUTC()));

        eventExplorer = new PrestoEventExplorer(queryExecutorService, prestoQueryExecutor, metastore);
        testingPrestoEventStore = new TestingPrestoEventStore(prestoQueryExecutor, prestoConfig);

        super.setUp();
    }

    @Override
    public EventStore getEventStore() {
        return testingPrestoEventStore;
    }

    @Override
    public Metastore getMetastore() {
        return metastore;
    }

    @Override
    public EventExplorer getEventExplorer() {
        return eventExplorer;
    }

    @AfterSuite
    public void destroy() throws Exception {
        testingEnvironment.close();
    }
}