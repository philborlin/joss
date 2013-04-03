package org.javaswift.joss.client.core;

import org.javaswift.joss.command.shared.factory.ContainerCommandFactory;
import org.javaswift.joss.headers.container.ContainerBytesUsed;
import org.javaswift.joss.headers.container.ContainerObjectCount;
import org.javaswift.joss.instructions.ListInstructions;
import org.javaswift.joss.instructions.SegmentationPlan;
import org.javaswift.joss.instructions.UploadInstructions;
import org.javaswift.joss.model.Account;
import org.javaswift.joss.model.Container;
import org.javaswift.joss.model.PaginationMap;
import org.javaswift.joss.model.StoredObject;
import org.javaswift.joss.exception.CommandException;
import org.javaswift.joss.headers.Metadata;
import org.javaswift.joss.headers.container.ContainerMetadata;
import org.javaswift.joss.information.ContainerInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public abstract class AbstractContainer extends AbstractObjectStoreEntity<ContainerInformation> implements Container {

    public static final Logger LOG = LoggerFactory.getLogger(UploadInstructions.class);

    private static final Integer MAX_PAGE_SIZE = 9999;

    protected final String name;

    private final Account account;

    private final ContainerCommandFactory commandFactory;

    public AbstractContainer(ContainerCommandFactory commandFactory, Account account, String name, boolean allowCaching) {
        super(allowCaching);
        this.commandFactory = commandFactory;
        this.name = name;
        this.account = account;
        this.info = new ContainerInformation();
    }

    public void metadataSetFromHeaders() {
        this.staleHeaders = false;
    }

    public Collection<StoredObject> list() {
        return list(null, null, getMaxPageSize());
    }

    public Collection<StoredObject> list(PaginationMap paginationMap, int page) {
        return list(paginationMap.getPrefix(), paginationMap.getMarker(page), paginationMap.getPageSize());
    }

    public PaginationMap getPaginationMap(String prefix, int pageSize) {
        return new ContainerPaginationMap(this, prefix, pageSize).buildMap();
    }

    public PaginationMap getPaginationMap(int pageSize) {
        return getPaginationMap(null, pageSize);
    }

    public StoredObject getObjectSegment(String name, int part) {
        return getObject(name + "/" + String.format("%08d", part));
    }

    public int getCount() {
        checkForInfoAndAllowHeaderSet();
        return info.getObjectCount();
    }

    public long getBytesUsed() {
        checkForInfoAndAllowHeaderSet();
        return info.getBytesUsed();
    }

    public void setCount(int count) {
        info.setObjectCount(new ContainerObjectCount(Integer.toString(count)));
    }

    public void setBytesUsed(long bytesUsed) {
        info.setBytesUsed(new ContainerBytesUsed(Long.toString(bytesUsed)));
    }

    public boolean isPublic() {
        checkForInfo();
        return info.isPublicContainer();
    }

    public String getName() {
        return name;
    }

    public Account getAccount() {
        return account;
    }

    public int hashCode() {
        return getName().hashCode();
    }

    @SuppressWarnings("ConstantConditions")
    public boolean equals(Object o) {
        return o instanceof Container && getName().equals(((Container) o).getName());
    }

    @SuppressWarnings("ConstantConditions")
    public int compareTo(Container o) {
        return getName().compareTo(o.getName());
    }

    protected Metadata createMetadataEntry(String name, String value) {
        return new ContainerMetadata(name, value);
    }

    public void uploadSegmentedObjects(String name, UploadInstructions uploadInstructions) {
        String path = getName()+"/"+name;
        try {
            LOG.info("JOSS / Setting up a segmentation plan for "+path);
            SegmentationPlan plan = uploadInstructions.getSegmentationPlan();
            InputStream segmentStream = plan.getNextSegment();
            while (segmentStream != null) {
                LOG.info("JOSS / Uploading segment "+plan.getSegmentNumber());
                StoredObject segment = getObjectSegment(name, plan.getSegmentNumber().intValue());
                segment.uploadObject(segmentStream);
                segmentStream.close();
                segmentStream = plan.getNextSegment();
            }
        } catch (IOException err) {
            LOG.error("JOSS / Failed to set up a segmentation plan for "+path+": "+err.getMessage());
            throw new CommandException("Unable to upload segments", err);
        }
    }

    public int getMaxPageSize() {
        return MAX_PAGE_SIZE;
    }

    public ContainerCommandFactory getFactory() {
        return this.commandFactory;
    }

    public void makePublic() {
        setContainerRights(true);
    }

    public void makePrivate() {
        setContainerRights(false);
    }

    public void setContainerRights(boolean publicContainer) {
        commandFactory.createContainerRightsCommand(getAccount(), this, publicContainer).call();
        this.info.setPublicContainer(publicContainer);
    }

    public Collection<StoredObject> list(String prefix, String marker, int pageSize) {
        ListInstructions listInstructions = new ListInstructions()
                .setPrefix(prefix)
                .setMarker(marker)
                .setLimit(pageSize);
        return commandFactory.createListObjectsCommand(getAccount(), this, listInstructions).call();
    }

    public Container create() {
        commandFactory.createCreateContainerCommand(getAccount(), this).call();
        return this;
    }

    public void delete() {
        commandFactory.createDeleteContainerCommand(getAccount(), this).call();
    }

    @Override
    protected void saveMetadata() {
        commandFactory.createContainerMetadataCommand(getAccount(), this, info.getMetadata()).call();
    }

    protected void getInfo(boolean allowErrorLog) {
        this.info = commandFactory.createContainerInformationCommand(getAccount(), this, allowErrorLog).call();
        this.setInfoRetrieved();
    }
}