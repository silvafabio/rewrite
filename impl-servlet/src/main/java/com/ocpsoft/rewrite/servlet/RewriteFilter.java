/*
 * Copyright 2011 <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ocpsoft.rewrite.servlet;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.ocpsoft.common.pattern.WeightedComparator;
import org.ocpsoft.common.services.ServiceLoader;
import org.ocpsoft.common.spi.ServiceEnricher;
import org.ocpsoft.common.util.Iterators;
import org.ocpsoft.logging.Logger;

import com.ocpsoft.rewrite.config.ConfigurationProvider;
import com.ocpsoft.rewrite.event.Rewrite;
import com.ocpsoft.rewrite.servlet.event.BaseRewrite.Flow;
import com.ocpsoft.rewrite.servlet.event.InboundServletRewrite;
import com.ocpsoft.rewrite.servlet.impl.RewriteContextImpl;
import com.ocpsoft.rewrite.servlet.spi.ContextListener;
import com.ocpsoft.rewrite.servlet.spi.InboundRewriteProducer;
import com.ocpsoft.rewrite.servlet.spi.OutboundRewriteProducer;
import com.ocpsoft.rewrite.servlet.spi.RequestCycleWrapper;
import com.ocpsoft.rewrite.servlet.spi.RequestListener;
import com.ocpsoft.rewrite.servlet.spi.RequestParameterProvider;
import com.ocpsoft.rewrite.servlet.spi.RewriteLifecycleListener;
import com.ocpsoft.rewrite.spi.ExpressionLanguageProvider;
import com.ocpsoft.rewrite.spi.InvocationResultHandler;
import com.ocpsoft.rewrite.spi.RewriteProvider;
import com.ocpsoft.rewrite.util.ServiceLogger;

/**
 * {@link Filter} responsible for handling all inbound {@link Rewrite} events.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class RewriteFilter implements Filter
{

   Logger log = Logger.getLogger(RewriteFilter.class);

   private List<RewriteLifecycleListener<Rewrite>> listeners;
   private List<RequestCycleWrapper<ServletRequest, ServletResponse>> wrappers;
   private List<RewriteProvider<Rewrite>> providers;
   private List<InboundRewriteProducer<ServletRequest, ServletResponse>> inbound;
   private List<OutboundRewriteProducer<ServletRequest, ServletResponse, Object>> outbound;

   @Override
   @SuppressWarnings("unchecked")
   public void init(final FilterConfig filterConfig) throws ServletException
   {
      log.info("RewriteFilter starting up...");

      listeners = Iterators.asUniqueList(ServiceLoader.load(RewriteLifecycleListener.class));
      wrappers = Iterators.asUniqueList(ServiceLoader.load(RequestCycleWrapper.class));
      providers = Iterators.asUniqueList(ServiceLoader.load(RewriteProvider.class));
      inbound = Iterators.asUniqueList(ServiceLoader.load(InboundRewriteProducer.class));
      outbound = Iterators.asUniqueList(ServiceLoader.load(OutboundRewriteProducer.class));

      Collections.sort(listeners, new WeightedComparator());
      Collections.sort(wrappers, new WeightedComparator());
      Collections.sort(providers, new WeightedComparator());
      Collections.sort(inbound, new WeightedComparator());
      Collections.sort(outbound, new WeightedComparator());

      ServiceLogger.logLoadedServices(log, RewriteLifecycleListener.class, listeners);
      ServiceLogger.logLoadedServices(log, RequestCycleWrapper.class, wrappers);
      ServiceLogger.logLoadedServices(log, RewriteProvider.class, providers);
      ServiceLogger.logLoadedServices(log, InboundRewriteProducer.class, inbound);
      ServiceLogger.logLoadedServices(log, OutboundRewriteProducer.class, outbound);

      /*
       * Log more services for debug purposes only.
       */
      ServiceLogger.logLoadedServices(log, ContextListener.class,
               Iterators.asUniqueList(ServiceLoader.load(ContextListener.class)));

      ServiceLogger.logLoadedServices(log, RequestListener.class,
               Iterators.asUniqueList(ServiceLoader.load(RequestListener.class)));

      ServiceLogger.logLoadedServices(log, RequestParameterProvider.class,
               Iterators.asUniqueList(ServiceLoader.load(RequestParameterProvider.class)));

      ServiceLogger.logLoadedServices(log, ExpressionLanguageProvider.class,
               Iterators.asUniqueList(ServiceLoader.load(ExpressionLanguageProvider.class)));

      ServiceLogger.logLoadedServices(log, InvocationResultHandler.class,
               Iterators.asUniqueList(ServiceLoader.load(InvocationResultHandler.class)));

      ServiceLogger.logLoadedServices(log, ServiceEnricher.class,
               Iterators.asUniqueList(ServiceLoader.load(ServiceEnricher.class)));

      /*
       * Load ConfigurationProviders here solely so that we may log all known implementations at boot time.
       */
      List<ConfigurationProvider<?>> configurations = Iterators.asUniqueList(ServiceLoader
               .load(ConfigurationProvider.class));
      ServiceLogger.logLoadedServices(log, ConfigurationProvider.class, configurations);

      if ((configurations == null) || configurations.isEmpty())
      {
         log.warn("No ConfigurationProviders were registered: " +
                  "Rewrite will not be enabled on this application. " +
                  "Did you forget to create a '/META-INF/services/" + ConfigurationProvider.class.getName() +
                  " file containing the fully qualified name of your provider implementation?");
      }

      log.info("RewriteFilter initialized.");
   }

   @Override
   public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException
            {
      InboundServletRewrite<ServletRequest, ServletResponse> event = createRewriteEvent(request,
               response);

      if (event == null)
      {
         log.warn("No Rewrite event was produced - RewriteFilter disabled on this request.");
         chain.doFilter(request, response);
      }
      else
      {
         if (request.getAttribute(RewriteLifecycleContext.CONTEXT_KEY) == null)
         {
            RewriteLifecycleContext context = new RewriteContextImpl(inbound, outbound, listeners, wrappers, providers);
            request.setAttribute(RewriteLifecycleContext.CONTEXT_KEY, context);
         }

         for (RewriteLifecycleListener<Rewrite> listener : listeners)
         {
            if (listener.handles(event))
               listener.beforeInboundLifecycle(event);
         }

         for (RequestCycleWrapper<ServletRequest, ServletResponse> wrapper : wrappers)
         {
            if (wrapper.handles(event))
            {
               event.setRequest(wrapper.wrapRequest(event.getRequest(), event.getResponse()));
               event.setResponse(wrapper.wrapResponse(event.getRequest(), event.getResponse()));
            }
         }

         rewrite(event);

         if (!event.getFlow().is(Flow.ABORT_REQUEST))
         {
            log.debug("RewriteFilter passing control of request to underlying application.");
            chain.doFilter(event.getRequest(), event.getResponse());
            log.debug("Control of request returned to RewriteFilter.");
         }

         for (RewriteLifecycleListener<Rewrite> listener : listeners)
         {
            if (listener.handles(event))
               listener.afterInboundLifecycle(event);
         }
      }
            }

   public InboundServletRewrite<ServletRequest, ServletResponse> createRewriteEvent(final ServletRequest request,
            final ServletResponse response)
            {
      for (InboundRewriteProducer<ServletRequest, ServletResponse> producer : inbound)
      {
         InboundServletRewrite<ServletRequest, ServletResponse> event = producer
                  .createInboundRewrite(request, response);
         if (event != null)
            return event;
      }
      return null;
            }

   private void rewrite(final InboundServletRewrite<ServletRequest, ServletResponse> event)
            throws ServletException,
            IOException
            {
      for (RewriteLifecycleListener<Rewrite> listener : listeners)
      {
         if (listener.handles(event))
            listener.beforeInboundRewrite(event);
      }

      for (RewriteProvider<Rewrite> provider : providers)
      {
         if (provider.handles(event))
         {
            provider.rewrite(event);

            if (event.getFlow().is(Flow.HANDLED))
            {
               log.debug("Event flow marked as HANDLED. No further processing will occur.");
               break;
            }
         }
      }

      for (RewriteLifecycleListener<Rewrite> listener : listeners)
      {
         if (listener.handles(event))
            listener.afterInboundRewrite(event);
      }

      if (event.getFlow().is(Flow.ABORT_REQUEST))
      {
         if (event.getFlow().is(Flow.FORWARD))
         {
            log.debug("Issuing internal FORWARD to [{}].", event.getDispatchResource());
            event.getRequest().getRequestDispatcher(event.getDispatchResource())
            .forward(event.getRequest(), event.getResponse());
         }
         else if (event.getFlow().is(Flow.REDIRECT_PERMANENT))
         {
            log.debug("Issuing 301 permanent REDIRECT to [{}].", event.getDispatchResource());
            HttpServletResponse response = (HttpServletResponse) event.getResponse();
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", event.getDispatchResource());
            response.flushBuffer();
         }
         else if (event.getFlow().is(Flow.REDIRECT_TEMPORARY))
         {
            log.debug("Issuing 302 temporary REDIRECT to [{}].", event.getDispatchResource());
            HttpServletResponse response = (HttpServletResponse) event.getResponse();
            response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
            response.setHeader("Location", event.getDispatchResource());
            response.flushBuffer();
         }
         else
         {
            log.debug("ABORT requested. Terminating request NOW.");
         }
      }
      else if (event.getFlow().is(Flow.INCLUDE))
      {
         log.debug("Issuing internal INCLUDE to [{}].", event.getDispatchResource());
         event.getRequest().getRequestDispatcher(event.getDispatchResource())
         .include(event.getRequest(), event.getResponse());
      }
            }

   @Override
   public void destroy()
   {
      log.info("RewriteFilter shutting down...");
      log.info("RewriteFilter deactivated.");
   }

}
