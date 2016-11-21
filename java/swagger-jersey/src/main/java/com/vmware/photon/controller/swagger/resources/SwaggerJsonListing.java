/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.swagger.resources;

import com.vmware.photon.controller.swagger.api.SwaggerApiListing;
import com.vmware.photon.controller.swagger.api.SwaggerModel;
import com.vmware.photon.controller.swagger.api.SwaggerModelProperty;
import com.vmware.photon.controller.swagger.api.SwaggerOperation;
import com.vmware.photon.controller.swagger.api.SwaggerParameter;
import com.vmware.photon.controller.swagger.api.SwaggerResourceListing;
import com.vmware.photon.controller.swagger.api.SwaggerResourceListingPath;
import com.vmware.photon.controller.swagger.api.SwaggerResourceListings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;

import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// TODO(lisbakke): Make the paths configurable. https://www.pivotaltracker.com/story/show/52997693
// TODO(lisbakke): Cache the json listings. https://www.pivotaltracker.com/story/show/52997737
// TODO(lisbakke): Support @Produces and @Consumes https://www.pivotaltracker.com/story/show/52997909

/**
 * This class is responsible for serving swagger-compliant JSON. It responds to requests at /api-docs with a listing
 * of all resources that are documented. /api-docs/path/to/resource will output the docs for the given resource. This
 * class parses swagger annotations and turns them into their json equivalent for swagger-ui to consume.
 */
@Path(SwaggerJsonListing.SWAGGER_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SwaggerJsonListing {

  public static final String DEFAULT_API_VERSION = "1.0";

  public static final String SWAGGER_PATH = "/api-docs";
  public static final String QUERY_TYPE = "query";
  public static final String PATH_PARAM = "path";
  public static final String MATRIX_PARAM = "matrix";
  public static final String HEADER_PARAM = "header";
  public static final String FORM_PARAM = "form";
  public static final String COOKIE = "cookie";
  public static final String SWAGGER_VERSION = "1.2";
  private static final String REFERENCE_TYPE = "$ref";
  private static final List<String> COLLECTION_TYPES = Arrays.asList("List", "Array");
  private static final List<String> PRIMITIVE_TYPES = Arrays.asList(
      "byte", "boolean", "int", "long", "float", "double", "string", "date", "void",
      "Byte", "Boolean", "Integer", "Long", "Float", "Double", "String", "Date", "Void");
  private static final Object resourceListingsLock = new Object();
  private static Map<String, SwaggerResourceListing> resourceListingCacheMap = new ConcurrentHashMap<>();
  private static SwaggerResourceListings resourceListings = null;
  private final String apiVersion;
  private final String swaggerVersion;
  private List<Class<?>> resources;

  /**
   * The constructor for the swagger json listing. It allows swagger to know what resources to document,
   * which swagger-ui version is being used, and what the api version is of the api being documented.
   *
   * @param resources      A list of resource classes that are to be documented. Sub resources will be scanned
   *                       automatically.
   * @param swaggerVersion The version of swagger-ui that this should output json for.
   * @param apiVersion     The api version of the api being documented.
   */
  public SwaggerJsonListing(List<Class<?>> resources, String swaggerVersion, String apiVersion) {
    this.resources = resources;
    this.apiVersion = apiVersion;
    this.swaggerVersion = swaggerVersion;
  }

  public SwaggerJsonListing() {
    this.swaggerVersion = SWAGGER_VERSION;
    this.apiVersion = DEFAULT_API_VERSION;
  }

  public static void clearCaches() {
    resourceListingCacheMap.clear();
    synchronized (resourceListingsLock) {
      resourceListings = null;
    }
  }

  /**
   * This outputs the json that contains a list of all resources that are documented,
   * and the paths for getting their documentation.
   *
   * @return SwaggerResourceListings The API representation for the master list of documented resources.
   */
  @GET
  public SwaggerResourceListings getSwaggerResourceListings() {
    synchronized (resourceListingsLock) {
      if (resourceListings != null) {
        return resourceListings;
      }
    }
    SwaggerResourceListings listings = new SwaggerResourceListings();
    listings.setBasePath("");
    listings.setApiVersion(this.apiVersion);
    listings.setSwaggerVersion(this.swaggerVersion);

    List<SwaggerResourceListingPath> apis = new ArrayList<>();
    Set<String> apiValues = new HashSet<>();
    for (Class<?> resource : resources) {
      if (resource.isAnnotationPresent(Api.class)) {
        SwaggerResourceListingPath swaggerResourcePath = new SwaggerResourceListingPath();
        Api api = resource.getAnnotation(Api.class);
        if (!apiValues.contains(api.value())) {
          swaggerResourcePath.setPath(UriBuilder.fromPath(SWAGGER_PATH).path(api.value()).build()
              .toString());
          apis.add(swaggerResourcePath);
          apiValues.add(api.value());
        }
      }
    }
    listings.setApis(apis);
    synchronized (resourceListingsLock) {
      resourceListings = listings;
    }
    return listings;
  }

  /**
   * This is for getting the documentation for a specific resource and all of its subresources. An example request
   * that this handles would be /api-docs/v1/projects. This function would then look for a registered resource with
   * the path /v1/projects and it would generate documentation for it.
   *
   * @param resourcepath The path parameter that represents the path of the resource to document. For example,
   *                     /v1/projects.
   * @return SwaggerResourceListing The API representation for the swagger documentation of a single resource class.
   * @throws ClassNotFoundException
   */
  @GET
  @Path("/{resourcepath:.*}")
  public SwaggerResourceListing getSwaggerResourceListing(@PathParam("resourcepath") String resourcepath) throws
      ClassNotFoundException {
    if (!resourcepath.startsWith("/")) {
      resourcepath = "/" + resourcepath;
    }

    if (resourceListingCacheMap.containsKey(resourcepath)) {
      return resourceListingCacheMap.get(resourcepath);
    }

    Map<Resource, Class<?>> resourcesMap = new HashMap<>();
    // Find the resource with the path equal to resourcepath.
    for (Class<?> resource : resources) {
      Path path = resource.getAnnotation(Path.class);
      if (path != null) {
        if (path.value().startsWith(resourcepath)) {
          resourcesMap.put(Resource.from(resource), resource);
        }
      }
    }

    if (resourcesMap.isEmpty()) {
      return null;
    }


    // Start filling out the API representation.
    SwaggerResourceListing listing = new SwaggerResourceListing();
    HashMap<String, SwaggerModel> models = new LinkedHashMap<>();
    List<SwaggerApiListing> allApiListings = new ArrayList<>();
    for (Resource resource : resourcesMap.keySet()) {
      // This does the bulk of the work. It will be called recursively for subresources.
      List<SwaggerApiListing> apiListings = getApiListings(
          models, resource,
          UriBuilder.fromPath("/"),
          new ArrayList<>(),
          resourcesMap);
      allApiListings.addAll(apiListings);
    }


    listing.setApis(allApiListings);
    listing.setModels(models);
    listing.setBasePath("");
    listing.setApiVersion(this.apiVersion);
    listing.setSwaggerVersion(this.swaggerVersion);
    listing.setResourcePath(resourcepath);

    resourceListingCacheMap.put(resourcepath, listing);

    return listing;
  }

  /**
   * Parses annotations and turns them into API representations. It goes through a resource class that is annotated
   * with @Api and will generate docs for all HTTP endpoints, the parameters they take, the values they return,
   * and create JSON models of every non-primitive type that is encountered so that swagger-ui can accurately display
   * the information for parameter and return types. It also will recursively call itself when it finds return values
   * in methods that are subresources.
   *
   * @param models       A hashmap of the class name to a SwaggerModel api representation. This is passed all around so
   *                     that anytime a new type is encountered it can be documented.
   * @param resource     The resource class we are documenting methods, parameters, return types for.
   * @param uriBuilder   This uriBuilder is passed around so that subresources can keep track of the uri path of parent
   *                     resources.
   * @param parentParams The parameters that a parent resource takes, which a subresource would need to document.
   * @return
   */
  private List<SwaggerApiListing> getApiListings(HashMap<String, SwaggerModel> models,
                                                 Resource resource,
                                                 UriBuilder uriBuilder,
                                                 List<SwaggerParameter> parentParams,
                                                 Map<Resource, Class<?>> resourcesMap) {
    Class<?> resourceClass = resourcesMap.get(resource);
    List<SwaggerApiListing> apiListings = new ArrayList<>();
    if (resourceClass.isAnnotationPresent(Path.class)) {
      Path methodPath = resourceClass.getAnnotation(Path.class);
      uriBuilder.path(methodPath.value());
    }
    if (resourceClass.isAnnotationPresent(Api.class)) {
      List<ResourceMethod> resourceMethods = new ArrayList<>(resource.getResourceMethods());
      for (Resource childResource : resource.getChildResources()) {
        resourceMethods.addAll(childResource.getAllMethods());
      }
      for (ResourceMethod method : resourceMethods) {
        apiListings = concatListings(apiListings, getMethodData(models, method, uriBuilder.clone(), parentParams,
            resourcesMap));
      }
    }

    return apiListings;
  }

  /**
   * Concatenates two lists of SwaggerApiListing. It is not just a normal concat. The 'path' property must be looked
   * at. If two SwaggerApiListing have the same path, then they should be merged into one listing and have their
   * operations merged. This could happen in the case where there are multiple HTTP operations for one path.
   *
   * @param listingsA The first list.
   * @param listingsB The second list.
   * @return A merged list.
   */
  private List<SwaggerApiListing> concatListings(List<SwaggerApiListing> listingsA,
                                                 List<SwaggerApiListing> listingsB) {
    List<SwaggerApiListing> finalList = new ArrayList<>();
    listingsA.addAll(listingsB);
    for (SwaggerApiListing listingA : listingsA) {
      boolean added = false;
      for (SwaggerApiListing finalListing : finalList) {
        if (listingA.getPath().equals(finalListing.getPath())) {
          added = true;
          finalListing.getOperations().addAll(listingA.getOperations());
          break;
        }
      }
      if (!added) {
        finalList.add(listingA);
      }
    }
    return finalList;
  }

  /**
   * Looks at a method's annotations (return type, parameters, path, etc.) and adds them to the API representations.
   * If a return type is a subresource this method will recurse back to getApiListings to have it parsed.
   *
   * @param models       The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param method       The method to document.
   * @param uriBuilder   A uribuilder that helps this resource determine its true http path.
   * @param parentParams The parameters of parent resources, so that they can be documented by subresources.
   * @return A list of SwaggerApiListing.
   */
  private List<SwaggerApiListing> getMethodData(HashMap<String, SwaggerModel> models,
                                                ResourceMethod method,
                                                UriBuilder uriBuilder,
                                                List<SwaggerParameter> parentParams,
                                                Map<Resource, Class<?>> resourcesMap) {
    Method definitionMethod = method.getInvocable().getDefinitionMethod();
    Class<?> returnType = definitionMethod.getReturnType();
    boolean returnTypeIsSubResource = false;
    Resource returnResource = null;

    // If the return type isn't primitive, it could be a subresource type.
    if (!PRIMITIVE_TYPES.contains(returnType.getSimpleName())) {
      returnResource = Resource.from(returnType);
      returnTypeIsSubResource = returnResource != null &&
          resourcesMap.containsKey(returnResource) &&
          resourcesMap.get(returnResource).isAnnotationPresent(Api.class);
    }

    Path methodPath = definitionMethod.getAnnotation(Path.class);
    if (methodPath != null) {
      uriBuilder.path(methodPath.value());
    }

    if (returnTypeIsSubResource) {
      // If the return type is a subresource, recurse back to getApiListings.
      return getApiListings(
          models, returnResource,
          uriBuilder.clone(),
          getOperation(models, method, parentParams).getParameters(),
          resourcesMap);
    } else {
      // Generate the docs for this endpoint.
      SwaggerOperation operation = getOperation(models, method, parentParams);
      addModel(models, returnType);
      List<SwaggerOperation> operations = new ArrayList<>();
      operations.add(operation);

      SwaggerApiListing listing = new SwaggerApiListing();
      listing.setPath(uriBuilder.toTemplate());
      listing.setOperations(operations);

      List<SwaggerApiListing> apiListings = new ArrayList<>();
      apiListings.add(listing);
      return apiListings;
    }
  }

  /**
   * Gets all of the operation data for a method, such as http method, documentation, parameters, response class, etc.
   *
   * @param models       The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param method       The method to document.
   * @param parentParams The parameters of parent resources, so that they can be documented by subresources.
   * @return A SwaggerOperation API representation.
   */
  private SwaggerOperation getOperation(HashMap<String, SwaggerModel> models, ResourceMethod method,
                                        List<SwaggerParameter> parentParams) {
    Method definitionMethod = method.getInvocable().getDefinitionMethod();
    SwaggerOperation operation = new SwaggerOperation();

    // If there is no @ApiOperation on a method we still document it because it's possible that the return class of
    // this method is a resource class with @Api and @ApiOperation on methods.
    ApiOperation apiOperation = definitionMethod.getAnnotation(ApiOperation.class);
    if (apiOperation != null) {
      operation.setSummary(apiOperation.value());
      operation.setNotes(apiOperation.notes());
      Class<?> responseClass = apiOperation.response().equals(Void.class) ?
          definitionMethod.getReturnType() : apiOperation.response();
      if (StringUtils.isNotBlank(apiOperation.responseContainer())) {
        operation.setResponseClass(addListModel(models, responseClass, apiOperation.responseContainer()));
      } else {
        operation.setResponseClass(getTypeName(models, responseClass, definitionMethod.getGenericReturnType()));
      }

      addModel(models, responseClass);
    }

    operation.setHttpMethod(parseHttpOperation(definitionMethod));
    operation.setNickname(definitionMethod.getName());

    // In this block we get all of the parameters to the method and convert them to SwaggerParameter types. We
    // introspect the generic types.
    List<SwaggerParameter> swaggerParameters = new ArrayList<>();
    Class<?>[] parameterTypes = definitionMethod.getParameterTypes();
    Type[] genericParameterTypes = definitionMethod.getGenericParameterTypes();
    Annotation[][] parameterAnnotations = definitionMethod.getParameterAnnotations();
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> parameter = parameterTypes[i];

      Type genericParameterType = genericParameterTypes[i];
      if (genericParameterType instanceof Class &&
          "javax.ws.rs.core.Request".equals(((Class<?>) genericParameterType).getName())) {
        continue;
      }

      SwaggerParameter swaggerParameter = parameterToSwaggerParameter(models, parameter, genericParameterType,
          parameterAnnotations[i]);
      swaggerParameters.add(swaggerParameter);
      // Add this parameter to the list of model documentation.
      addModel(models, parameter);
    }
    swaggerParameters.addAll(parentParams);
    operation.setParameters(swaggerParameters);
    return operation;
  }

  /**
   * Given a parameter to a method, this method will introspect it and convert it into a SwaggerParameter API
   * representation.
   *
   * @param models               The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param parameter            The parameter to introspect.
   * @param genericParameterType The generics information for this parameter.
   * @param parameterAnnotation  The annotations for the parameter.
   * @return A SwaggerParameter.
   */
  private SwaggerParameter parameterToSwaggerParameter(HashMap<String, SwaggerModel> models, Class<?> parameter,
                                                       Type genericParameterType, Annotation[] parameterAnnotation) {
    SwaggerParameter swaggerParameter = new SwaggerParameter();
    swaggerParameter.setDataType(getTypeName(models, parameter, genericParameterType));
    for (Annotation paramAnnotation : parameterAnnotation) {
      if (paramAnnotation instanceof QueryParam) {
        swaggerParameter.setParamType(QUERY_TYPE);
        swaggerParameter.setName(((QueryParam) paramAnnotation).value());
      } else if (paramAnnotation instanceof PathParam) {
        swaggerParameter.setParamType(PATH_PARAM);
        swaggerParameter.setName(((PathParam) paramAnnotation).value());
        swaggerParameter.setRequired(true);
      } else if (paramAnnotation instanceof MatrixParam) {
        swaggerParameter.setParamType(MATRIX_PARAM);
        swaggerParameter.setName(((MatrixParam) paramAnnotation).value());
      } else if (paramAnnotation instanceof HeaderParam) {
        swaggerParameter.setParamType(HEADER_PARAM);
        swaggerParameter.setName(((HeaderParam) paramAnnotation).value());
      } else if (paramAnnotation instanceof FormParam) {
        swaggerParameter.setParamType(FORM_PARAM);
        swaggerParameter.setName(((FormParam) paramAnnotation).value());
      } else if (paramAnnotation instanceof CookieParam) {
        swaggerParameter.setParamType(COOKIE);
        swaggerParameter.setName(((CookieParam) paramAnnotation).value());
      } else if (paramAnnotation instanceof ApiParam) {
        swaggerParameter.setRequired(((ApiParam) paramAnnotation).required());
        swaggerParameter.setDescription(((ApiParam) paramAnnotation).value());
        swaggerParameter.setDefaultValue(((ApiParam) paramAnnotation).defaultValue());
        String allowableValues = ((ApiParam) paramAnnotation).allowableValues();
        if (allowableValues != null && !allowableValues.isEmpty()) {
          swaggerParameter.setAllowableValues(parseAllowableValues(allowableValues));
        }
      }
    }
    return swaggerParameter;
  }

  /**
   * Given a class and the generic type for the class, this method will return a string that swagger-ui understands.
   * For instance, if the type is String it will just return String. But if the type is HashMap<String,
   * String> then this will return HashMap[String, String].
   *
   * @param models      The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param mainClass   The class to get the type of.
   * @param genericType The generic type information for the class.
   * @return The string version of this class.
   */
  private String getTypeName(HashMap<String, SwaggerModel> models, Class<?> mainClass, Type genericType) {
    String returnTypeString = mainClass.getSimpleName();
    if (genericType instanceof ParameterizedType) {
      List<Type> genericTypes = Arrays.asList(((ParameterizedType) genericType).getActualTypeArguments());
      if (genericTypes.size() > 0) {
        List<String> namedTypes = new ArrayList<>();
        for (Type type : genericTypes) {
          addModel(models, (Class<?>) type);
          namedTypes.add(((Class<?>) type).getSimpleName());
        }
        returnTypeString += "[" + Joiner.on(",").join(namedTypes) + "]";
      }
    }
    return returnTypeString;
  }

  /**
   * Converts a List class to a SwaggerModel API representation and adds it to the models HashMap.
   * List<T> cannot be reflected and it's impossible to create class of List<model> at runtime for "type erasure"
   * reason.
   * We are generating a SwaggerModel specifically for List with most fields hardcoded.
   *
   * @param models    The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param model     The model to add to the models hashmap.
   * @param modelName The name of model.
   * @return The name of ListModel in the form of modelName<model>
   */
  private String addListModel(HashMap<String, SwaggerModel> models, Class<?> model, String modelName) {
    SwaggerModel swaggerModel = new SwaggerModel();
    swaggerModel.setId(modelName);
    swaggerModel.setName(modelName);

    SwaggerModelProperty modelProperty = new SwaggerModelProperty();
    modelProperty.setType("List");
    modelProperty.setRequired(true);
    HashMap<String, String> items = new HashMap<>();
    items.put(REFERENCE_TYPE, model.getSimpleName());
    modelProperty.setItems(items);
    HashMap<String, SwaggerModelProperty> modelProperties = new HashMap<String, SwaggerModelProperty>();
    modelProperties.put("items", modelProperty);
    swaggerModel.setProperties(modelProperties);
    String listModelName = String.format("%s<%s>", modelName, model.getSimpleName());
    models.put(listModelName, swaggerModel);
    return listModelName;
  }

  /**
   * Converts a class to a SwaggerModel API representation and adds it to the models HashMap. Any property of the
   * class that is a @JsonProperty will be documented. @ApiModelProperty annotations are documented
   * and addModel will be called recursively with any properties which reference other classes that should be
   * documented as well. For instance, if Class1 has a property that is List[Class2],
   * then addModel will be called with Class2.
   *
   * @param models The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param model  The model to add to the models hashmap.
   */
  private void addModel(HashMap<String, SwaggerModel> models, Class<?> model) {
    String modelName = model.getSimpleName();
    if (!models.containsKey(modelName)) {
      SwaggerModel swaggerModel = new SwaggerModel();
      swaggerModel.setId(modelName);
      swaggerModel.setName(modelName);
      HashMap<String, SwaggerModelProperty> modelProperties = new HashMap<>();
      Class<?> curClass = model;
      List<Field> declaredFields = new ArrayList<>();
      // Get the properties from the class and its superclasses.
      while (curClass != null) {
        declaredFields.addAll(Arrays.asList(curClass.getDeclaredFields()));
        curClass = curClass.getSuperclass();
      }

      int numProperties = 0;
      // For each property, get it's data and possibly recurse to addModel.
      for (Field property : declaredFields) {
        if (property.getAnnotation(JsonProperty.class) != null) {
          SwaggerModelProperty modelProperty = new SwaggerModelProperty();
          String type = property.getType().getSimpleName();
          modelProperty = handleCollectionType(property, models, modelProperty);
          modelProperty.setType(type);
          ApiModelProperty apiModelProperty = property.getAnnotation(ApiModelProperty.class);
          if (apiModelProperty != null) {
            modelProperty.setDescription(apiModelProperty.value());
            modelProperty.setRequired(apiModelProperty.required());
            String allowableValues = apiModelProperty.allowableValues();
            if (allowableValues != null && !allowableValues.isEmpty()) {
              modelProperty.setAllowableValues(parseAllowableValues(apiModelProperty.allowableValues()));
            }
          }
          modelProperties.put(property.getName(), modelProperty);
          // Make sure this property of this class will also have a model built for it.
          addModel(models, property.getType());
          numProperties++;
        }
      }
      if (numProperties > 0) {
        swaggerModel.setProperties(modelProperties);
        models.put(modelName, swaggerModel);
      }
    }
  }

  /**
   * @param allowableValues The allowable values string to be parsed.
   * @return A HashMap which a SwaggerModelProperty's allowableValues should be set to.
   * @ApiParam and @ApiModelProperty have an attribute 'allowableValues'. This string can be one of two things -- a
   * list of possible parameters, or a range. A list would look like allowableValues="val1,val2,
   * val3" and a range would look like allowableValues="range[0,199]". This function parses these strings and
   * converts them to the JSON that swagger-ui understands.
   */
  private HashMap<String, Object> parseAllowableValues(String allowableValues) {
    HashMap<String, Object> parsedAllowableValues = new HashMap<>();
    if (allowableValues.startsWith("range[")) {
      String[] ranges = allowableValues.substring(6, allowableValues.length() - 1).split(",");
      if (ranges.length == 2) {
        // Swagger ui range values are broken.
        parsedAllowableValues.put("valueType", "RANGE");
        parsedAllowableValues.put("max", ranges[0]);
        parsedAllowableValues.put("min", ranges[1]);
      }
    } else {
      String[] values = allowableValues.split(",");
      parsedAllowableValues.put("valueType", "LIST");
      parsedAllowableValues.put("values", values);
    }
    return parsedAllowableValues;
  }

  /**
   * Converts a model property to a SwaggerModelProperty in the event that it is a collection type.
   *
   * @param property      The model property to convert.
   * @param models        The hashmap of models that are being documented so that they can be referenced by swagger-ui.
   * @param modelProperty The SwaggerModelProperty to convert this property into.
   * @return The converted SwaggerModelProperty.
   */
  private SwaggerModelProperty handleCollectionType(Field property, HashMap<String, SwaggerModel> models,
                                                    SwaggerModelProperty modelProperty) {
    String type = property.getType().getSimpleName();
    if (COLLECTION_TYPES.contains(type)) {
      ParameterizedType parameterizedType = (ParameterizedType) property.getGenericType();
      Type[] genericTypes = parameterizedType.getActualTypeArguments();
      if (genericTypes.length > 0) {
        // Swagger UI doesn't support the fact that a Parameter could be a collection/generic that
        // references two classes, such as HashMap<Class1, Class2>. If it did, then we wouldn't limit this method to
        // just COLLECTION_TYPES.
        Class<?> genericClass = (Class<?>) genericTypes[0];
        addModel(models, genericClass);
        HashMap<String, String> items = new HashMap<>();
        if (PRIMITIVE_TYPES.contains(genericClass.getSimpleName())) {
          items.put("type", genericClass.getSimpleName());
        } else {
          items.put(REFERENCE_TYPE, genericClass.getSimpleName());
        }
        modelProperty.setItems(items);
      }
    }
    return modelProperty;
  }

  /**
   * Looks at a method and determines the HTTP operation that it accepts.
   *
   * @param method The method to introspect.
   * @return A string name of the HTTP operation.
   */
  private String parseHttpOperation(Method method) {
    if (method.getAnnotation(GET.class) != null) {
      return "GET";
    } else if (method.getAnnotation(DELETE.class) != null) {
      return "DELETE";
    } else if (method.getAnnotation(POST.class) != null) {
      return "POST";
    } else if (method.getAnnotation(PUT.class) != null) {
      return "PUT";
    } else if (method.getAnnotation(HEAD.class) != null) {
      return "HEAD";
    } else if (method.getAnnotation(OPTIONS.class) != null) {
      return "OPTIONS";
    }
    return null;
  }

}
