/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.fabric.domain;

import javax.xml.namespace.QName;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.fabric3.api.host.Names;
import org.fabric3.api.host.domain.AssemblyException;
import org.fabric3.api.host.domain.DeploymentException;
import org.fabric3.api.host.runtime.DefaultHostInfo;
import org.fabric3.api.host.runtime.HostInfo;
import org.fabric3.api.model.type.RuntimeMode;
import org.fabric3.api.model.type.component.ComponentDefinition;
import org.fabric3.api.model.type.component.Composite;
import org.fabric3.api.model.type.definitions.PolicySet;
import org.fabric3.fabric.domain.collector.Collector;
import org.fabric3.fabric.domain.collector.CollectorImpl;
import org.fabric3.fabric.domain.instantiator.InstantiationContext;
import org.fabric3.fabric.domain.instantiator.LogicalModelInstantiator;
import org.fabric3.spi.contribution.Contribution;
import org.fabric3.spi.contribution.MetaDataStore;
import org.fabric3.spi.domain.Deployer;
import org.fabric3.spi.domain.generator.Deployment;
import org.fabric3.spi.domain.generator.Generator;
import org.fabric3.spi.domain.generator.binding.BindingSelector;
import org.fabric3.spi.domain.generator.policy.PolicyAttacher;
import org.fabric3.spi.domain.generator.policy.PolicyRegistry;
import org.fabric3.spi.model.instance.LogicalCompositeComponent;

/**
 * Verifies the distributed domain when run in Single-VM, transactional mode.
 */
public class DistributedDomainVMTestCase extends TestCase {
    private static final URI COMPONENT_URI = URI.create("fabric3://domain/component");
    private static final URI CONTRIBUTION_URI = URI.create("contribution");

    private static final QName DEPLOYABLE = new QName("foo", "bar");

    private IMocksControl control;
    private DistributedDomain domain;
    private LogicalModelInstantiator instantiator;
    private PolicyAttacher policyAttacher;
    private BindingSelector bindingSelector;
    private Generator generator;
    private Deployer deployer;
    private LogicalComponentManagerImpl lcm;

    private Contribution contribution;
    private ComponentDefinition componentDefinition;
    private Composite composite;

    public void testInclude() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createAnswer(componentDefinition);
        EasyMock.expect(instantiator.include(EasyMock.eq(composite), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        policyAttacher.attachPolicies(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean());
        bindingSelector.selectBindings(EasyMock.isA(LogicalCompositeComponent.class));

        Deployment deployment = new Deployment();
        EasyMock.expect(generator.generate(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean())).andReturn(deployment);
        deployer.deploy(EasyMock.isA(Deployment.class));

        control.replay();

        domain.include(DEPLOYABLE);

        // verify the component contained in the composite was added to the logical model
        assertNotNull(lcm.getRootComponent().getComponent(COMPONENT_URI));
        assertTrue(contribution.getLockOwners().contains(DEPLOYABLE));
        control.verify();
    }

    @SuppressWarnings({"unchecked"})
    public void testIncludeUris() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createAnswer(componentDefinition);
        EasyMock.expect(instantiator.include((List<Composite>) EasyMock.notNull(), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        policyAttacher.attachPolicies(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean());
        bindingSelector.selectBindings(EasyMock.isA(LogicalCompositeComponent.class));

        Deployment deployment = new Deployment();
        EasyMock.expect(generator.generate(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean())).andReturn(deployment);
        deployer.deploy(EasyMock.isA(Deployment.class));

        control.replay();

        domain.include(Collections.<URI>singletonList(CONTRIBUTION_URI));

        // verify the component contained in the composite was added to the logical model
        assertNotNull(lcm.getRootComponent().getComponent(COMPONENT_URI));
        control.verify();
    }

    public void testIncludeAndRemove() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createAnswer(componentDefinition);
        EasyMock.expect(instantiator.include(EasyMock.eq(composite), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        policyAttacher.attachPolicies(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean());
        bindingSelector.selectBindings(EasyMock.isA(LogicalCompositeComponent.class));

        Deployment deployment = new Deployment();
        EasyMock.expect(generator.generate(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean())).andReturn(deployment).times(2);
        deployer.deploy(EasyMock.isA(Deployment.class));
        EasyMock.expectLastCall().times(2);
        control.replay();

        domain.include(DEPLOYABLE);

        assertNotNull(lcm.getRootComponent().getComponent(COMPONENT_URI));

        domain.undeploy(composite.getContributionUri(), false);
        // verify the component contained in the composite was added to the logical model
        assertNull(lcm.getRootComponent().getComponent(COMPONENT_URI));
        control.verify();
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public void testInstantiationError() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createErrorAnswer(componentDefinition);
        EasyMock.expect(instantiator.include(EasyMock.eq(composite), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        control.replay();

        try {
            domain.include(DEPLOYABLE);
            fail();
        } catch (AssemblyException e) {
            // expected
            assertFalse(e.getErrors().isEmpty());
        }
        // verify the component contained in the composite was *not* added to the logical model
        assertNull(lcm.getRootComponent().getComponent(COMPONENT_URI));
        assertTrue(contribution.getLockOwners().isEmpty());
        control.verify();
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public void testDeploymentError() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createAnswer(componentDefinition);
        EasyMock.expect(instantiator.include(EasyMock.eq(composite), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        policyAttacher.attachPolicies(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean());
        bindingSelector.selectBindings(EasyMock.isA(LogicalCompositeComponent.class));

        Deployment deployment = new Deployment();
        EasyMock.expect(generator.generate(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean())).andReturn(deployment);
        deployer.deploy(EasyMock.isA(Deployment.class));
        // simulate a deployment exception
        EasyMock.expectLastCall().andThrow(new DeploymentException());

        control.replay();

        try {
            domain.include(DEPLOYABLE);
            fail();
        } catch (DeploymentException e) {
            // expected
        }
        // verify the component contained in the composite was *not* added to the logical model
        assertNull(lcm.getRootComponent().getComponent(COMPONENT_URI));
        assertTrue(contribution.getLockOwners().isEmpty());
        control.verify();
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public void testActivateDeactivateDefinitions() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createAnswer(componentDefinition);
        EasyMock.expect(instantiator.include(EasyMock.eq(composite), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        Deployment deployment = new Deployment();
        EasyMock.expect(generator.generate(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean())).andReturn(deployment).times(2);
        deployer.deploy(EasyMock.isA(Deployment.class));
        EasyMock.expectLastCall().times(2);

        PolicyRegistry policyRegistry = control.createMock(PolicyRegistry.class);
        Set<PolicySet> set = new HashSet<>();
        set.add(new PolicySet(new QName("foo", "bar"), null, null, null, null, null, null, null));
        EasyMock.expect(policyRegistry.activateDefinitions(CONTRIBUTION_URI)).andReturn(set);
        EasyMock.expect(policyRegistry.deactivateDefinitions(CONTRIBUTION_URI)).andReturn(set);
        domain.setPolicyRegistry(policyRegistry);

        policyAttacher.attachPolicies(EasyMock.isA(Set.class), EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean());
        policyAttacher.detachPolicies(EasyMock.isA(Set.class), EasyMock.isA(LogicalCompositeComponent.class));

        control.replay();

        domain.activateDefinitions(CONTRIBUTION_URI);

        domain.deactivateDefinitions(CONTRIBUTION_URI);
        control.verify();
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public void testActivateDefinitionsError() throws Exception {
        IAnswer<InstantiationContext> answer = DomainTestCaseHelper.createAnswer(componentDefinition);
        EasyMock.expect(instantiator.include(EasyMock.eq(composite), EasyMock.isA(LogicalCompositeComponent.class))).andStubAnswer(answer);

        Deployment deployment = new Deployment();
        EasyMock.expect(generator.generate(EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean())).andReturn(deployment);
        deployer.deploy(EasyMock.isA(Deployment.class));
        // simulate a deployment exception
        EasyMock.expectLastCall().andThrow(new DeploymentException());

        PolicyRegistry policyRegistry = control.createMock(PolicyRegistry.class);
        Set<PolicySet> set = new HashSet<>();
        set.add(new PolicySet(new QName("foo", "bar"), null, null, null, null, null, null, null));
        EasyMock.expect(policyRegistry.activateDefinitions(CONTRIBUTION_URI)).andReturn(set);
        domain.setPolicyRegistry(policyRegistry);

        policyAttacher.attachPolicies(EasyMock.isA(Set.class), EasyMock.isA(LogicalCompositeComponent.class), EasyMock.anyBoolean());

        control.replay();

        try {
            domain.activateDefinitions(CONTRIBUTION_URI);
            fail();
        } catch (DeploymentException e) {
            // expected
        }
        // verify the component contained in the composite was *not* added to the logical model as an error was raised during deployment
        assertNull(lcm.getRootComponent().getComponent(COMPONENT_URI));
        control.verify();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        URI uri = URI.create("fabric3://domain");
        HostInfo info = new DefaultHostInfo("runtime", Names.DEFAULT_ZONE, RuntimeMode.VM, null, uri, null, null, null, null, null, null, null, null, false);

        control = EasyMock.createControl();
        MetaDataStore store = control.createMock(MetaDataStore.class);

        ContributionHelperImpl helper = new ContributionHelperImpl(store, info);

        lcm = new LogicalComponentManagerImpl(info);
        lcm.init();

        generator = control.createMock(Generator.class);
        instantiator = control.createMock(LogicalModelInstantiator.class);

        policyAttacher = control.createMock(PolicyAttacher.class);
        bindingSelector = control.createMock(BindingSelector.class);
        deployer = control.createMock(Deployer.class);
        Collector collector = new CollectorImpl();
        domain = new DistributedDomain(store, lcm, generator, instantiator, policyAttacher, bindingSelector, deployer, collector, helper, info);
        domain.setTransactional(true);     // set transactional mode

        contribution = DomainTestCaseHelper.createContribution(store);
        componentDefinition = new ComponentDefinition("component");
        composite = DomainTestCaseHelper.createComposite(contribution, componentDefinition, store);
    }

}
