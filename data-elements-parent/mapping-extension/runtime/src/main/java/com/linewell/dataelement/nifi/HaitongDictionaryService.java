package com.linewell.dataelement.nifi;

import com.linewell.dataelement.mapping.core.DictionarySpec;
import org.apache.nifi.controller.ControllerService;

/** The only external-read entry point is a bounded set of keys. */
public interface HaitongDictionaryService extends ControllerService {
    DictionarySpec.Provider provider();
}
