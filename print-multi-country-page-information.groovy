package scripts


import de.hybris.platform.catalog.CatalogVersionService
import de.hybris.platform.catalog.model.CatalogVersionModel
import de.hybris.platform.cms2.model.contents.contentslot.ContentSlotModel
import de.hybris.platform.cms2.model.pages.AbstractPageModel
import de.hybris.platform.cms2.model.relations.ContentSlotForPageModel
import de.hybris.platform.cms2.model.relations.ContentSlotForTemplateModel
import de.hybris.platform.cms2.servicelayer.data.ContentSlotData
import de.hybris.platform.cms2.servicelayer.services.CMSPageService

import java.util.stream.Collectors

LOCAL_CATALOG = "myLocalCatalogUid"
GLOBAL_CATALOG = "myGlobalCatalogUid"

VERSION = "Online"
SOURCE_PAGE = "homepage"
CMSPageService cmsPageService = spring.getBean("defaultCMSPageService");
CatalogVersionService catalogVersionService = spring.getBean("catalogVersionService")

def globalContentCatalog = catalogVersionService.getCatalogVersion(GLOBAL_CATALOG, VERSION)
def localContentCatalog = catalogVersionService.getCatalogVersion(LOCAL_CATALOG, VERSION)

# Add catalogs to session
catalogVersionService.addSessionCatalogVersion(globalContentCatalog)
catalogVersionService.addSessionCatalogVersion(localContentCatalog)

AbstractPageModel sourcePage = cmsAdminPageService.getPageForId(SOURCE_PAGE, Collections.singleton(localContentCatalog))
Collection<ContentSlotData> contentSlotData = getContentSlotsForPage(sourcePage);

printPage(sourcePage)
contentSlotData.each {
  printSlot(it)
}


void printPage(sourcePage)
{
  println "* Page Name                       : " + sourcePage.getName()
  println "* Page Uid                        : " + sourcePage.getUid()
  println "* Catalog Version                 : " + sourcePage.getCatalogVersion().getCatalog().getId() + ":" + sourcePage.getCatalogVersion().getVersion()
  println "* Master Template                 : " + sourcePage.getMasterTemplate().getUid()
  println "* Master Template Catalog Version : " + sourcePage.getMasterTemplate().getCatalogVersion().getCatalog().getId() + ":" +  sourcePage.getMasterTemplate().getCatalogVersion().getVersion()
}

void printSlot(slot) {
  println "_______________________________________________________________________________________"
  println "* Name           : " + slot.getName()
  println "* UID            : " + slot.getUid()
  println "* isFromMaster   : " + slot.isFromMaster()
  println "* isOriginalSlot : " + slot.isOverrideSlot()
  println "* Position       : " + slot.getPosition()
  println "* Components     : "
  printComponents(slot)
}

void printComponents(slot) {
  slot.getCMSComponents().eachWithIndex {it, index ->
    println "     " + (index+1) + ". " + it.getName()
    println "            * Uid               : " + it.getUid()
    println "            * Catalogue Version : " + it.getCatalogVersion().getCatalog().getId() + ":" + it.getCatalogVersion().getVersion()
  }
}

public Collection<ContentSlotData> getContentSlotsForPage(AbstractPageModel page) {
  Collection<ContentSlotForPageModel> pageSlots = cmsPageService.getCmsContentSlotDao().findAllContentSlotRelationsByPage(page);
  Collection<ContentSlotForTemplateModel> templateSlots = cmsPageService.getPageTemplateSlots(page);
  return this.getAllContentSlotsForPageAndSlots(page, pageSlots, templateSlots);
}

# Method override only for this reason, to set IsOverrideSlot, which means, that the slot, has an originalSlot 
protected Collection<ContentSlotData> getAllContentSlotsForPageAndSlots(AbstractPageModel page, Collection<ContentSlotForPageModel> pageSlots, Collection<ContentSlotForTemplateModel> templateSlots) {
  List<String> positions = (List)pageSlots.stream().filter{o -> o != null}.map{slot -> slot.getPosition()}.collect(Collectors.toList());

  List<ContentSlotData> results = (List)pageSlots.stream().filter{o -> o != null}.map{pageSlotModel ->
    return cmsPageService.getCmsDataFactory().createContentSlotData(pageSlotModel);
  }.collect(Collectors.toList());
  List<ContentSlotModel> addedTemplateSlots = cmsPageService.appendContentSlots(results, positions, templateSlots, page);
  List<CatalogVersionModel> catalogVersions = new ArrayList(cmsPageService.getCatalogVersionService().getSessionCatalogVersions());
  List<ContentSlotModel> contentSlots = cmsPageService.getSortedMultiCountryContentSlots(addedTemplateSlots, catalogVersions);

  for(int i = 0; i < results.size(); ++i) {
    ContentSlotData slotData = (ContentSlotData)results.get(i);
    Optional<ContentSlotModel> overrideSlotOptional = cmsPageService.getOverrideSlot(contentSlots, slotData.getContentSlot());
    if (overrideSlotOptional.isPresent()) {
      ContentSlotData data = cmsPageService.getCmsDataFactory().createContentSlotData(slotData.getPageId(), (ContentSlotModel) overrideSlotOptional.get(), slotData.getPosition(), slotData.isFromMaster(), slotData.isAllowOverwrite())
      # Method override only for this reason, to set IsOverrideSlot, which means, that the slot, has an originalSlot 
      data.setIsOverrideSlot(true)
      results.set(i, data);
    }
  }
  return results;
}
