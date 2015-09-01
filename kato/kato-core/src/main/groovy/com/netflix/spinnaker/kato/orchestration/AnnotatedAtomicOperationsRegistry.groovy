/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.orchestration
import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.exceptions.CloudProviderNotFoundException
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired

import java.lang.annotation.Annotation

/**
 * @author sthadeshwar
 */
@Slf4j
class AnnotatedAtomicOperationsRegistry extends ApplicationContextAtomicOperationsRegistry {

  @Autowired
  List<CloudProvider> cloudProviders

  @Override
  AtomicOperationConverter getAtomicOperationConverter(String description, String cloudProvider) {
    // Legacy naming convention which is not generic and description name is specific to cloud provider
    try {
      AtomicOperationConverter converter = super.getAtomicOperationConverter(description, cloudProvider)
      if (converter) return converter
    } catch (NoSuchBeanDefinitionException e) {
      /**
       * If 'cloudProvider' is not specified then it means that caller was querying the bean as per the old cloud provider
       * specific name and if no bean found then we can't do anything here other than throwing the NoSuchBeanDefinitionException
       *
       * TO-DO: Once all the operations have been migrated as per the new naming scheme that is not cloud provider specific, then
       * make the 'description' and 'cloudProvider' arguments mandatory for this method
       */
      if (!cloudProvider) {
        throw e
      }
    }

    def providerAnnotation = getCloudProviderAnnotation(cloudProvider)

    List converters = applicationContext.getBeansWithAnnotation(AtomicOperationDescription).findAll { key, value ->
      value.getClass().getAnnotation(AtomicOperationDescription).value() == description &&
      value.getClass().getAnnotationsByType(providerAnnotation) != null
    }.values().toList()

    if (!converters) {
      throw new AtomicOperationConverterNotFoundException(
        "No atomic operation converter found for description '${description}' and cloud provider '${cloudProvider}'"
      )
    }

    if (converters.size() > 1) {
      throw new RuntimeException(
        "More than one atomic operation converters found for description '${description}' and cloud provider '${cloudProvider}'"
      )
    }

    return (AtomicOperationConverter) converters[0]
  }

  @Override
  DescriptionValidator getAtomicOperationDescriptionValidator(String validator, String cloudProvider) {
    // Legacy naming convention which is not generic and validator name is specific to cloud provider
    try {
      DescriptionValidator descriptionValidator = super.getAtomicOperationDescriptionValidator(validator, cloudProvider)
      if (descriptionValidator) {
        return descriptionValidator
      }
    } catch (NoSuchBeanDefinitionException e) {}

    if (!cloudProvider) return null

    def providerAnnotation = getCloudProviderAnnotation(cloudProvider)

    List validators = applicationContext.getBeansWithAnnotation(AtomicOperationDescriptionValidator).findAll { key, value ->
      value.getClass().getAnnotation(AtomicOperationDescriptionValidator).value() == validator &&
      value.getClass().getAnnotationsByType(providerAnnotation) != null
    }.values().toList()

    return validators ? (DescriptionValidator) validators[0] : null
  }

  protected Class<? extends Annotation> getCloudProviderAnnotation(String cloudProvider) {
    CloudProvider cloudProviderInstance = cloudProviders.find { it.id == cloudProvider }
    if (!cloudProviderInstance) {
      throw new CloudProviderNotFoundException("No cloud provider named '${cloudProvider}' found")
    }
    cloudProviderInstance.getAnnotation()
  }

}