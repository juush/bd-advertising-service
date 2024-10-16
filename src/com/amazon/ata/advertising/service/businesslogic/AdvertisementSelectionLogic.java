package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.AdvertisementContent;
import com.amazon.ata.advertising.service.model.EmptyGeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.GeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import javax.inject.Inject;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     * @param contentDao Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class.
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace and determines which content can be shown.  Returns the
     * eligible content with the highest click through rate.  If no advertisement is available or eligible, returns an
     * EmptyGeneratedAdvertisement.
     *
     * @param customerId - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     *     not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        GeneratedAdvertisement generatedAdvertisement = new EmptyGeneratedAdvertisement();

        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
        } else {
            final RequestContext requestContext = new RequestContext(customerId, marketplaceId);
            final TargetingEvaluator targetingEvaluator = new TargetingEvaluator(requestContext);
            final Comparator<TargetingGroup> comparator = Comparator.comparingDouble(TargetingGroup::getClickThroughRate).reversed();
            final List<AdvertisementContent> contents = contentDao.get(marketplaceId);
            final SortedMap<TargetingGroup, AdvertisementContent> contentsTree = new TreeMap<>(comparator);

            for (AdvertisementContent content : contents) {
                List<TargetingGroup> targetingGroups = targetingGroupDao.get(content.getContentId());
                targetingGroups.stream()
                        .sorted(comparator)
                        .filter(targetingGroup -> targetingEvaluator.evaluate(targetingGroup).isTrue())
                        .findFirst()
                        .ifPresent(targetingGroup -> contentsTree.put(targetingGroup, content));
            }

            if (MapUtils.isNotEmpty(contentsTree)) {
                final TargetingGroup firstKey = contentsTree.firstKey();
                final AdvertisementContent firstAdContent = contentsTree.get(firstKey);
                generatedAdvertisement = new GeneratedAdvertisement(firstAdContent);
            }
        }

        return generatedAdvertisement;
    }
}