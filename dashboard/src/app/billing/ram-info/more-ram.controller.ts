/*
 *  [2015] - [2017] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
'use strict';
import {CodenvySubscription} from '../../../components/api/codenvy-subscription.factory';
import {CodenvyResourceLimits} from '../../../components/api/codenvy-resource-limits';
import {ICreditCard} from '../../../components/api/codenvy-payment.factory';
import {BillingService} from '../billing.service';

enum Step {
  ONE = 1,
  TWO
}

export class MoreRamController {
  /**
   * Service for displaying dialogs.
   */
  private $mdDialog: angular.material.IDialogService;
  /**
   * Angular promise service.
   */
  private $q: ng.IQService;
  /**
   * Subscription API service.
   */
  private codenvySubscription: CodenvySubscription;
  /**
   *  Billing service.
   */
  private billingService: BillingService;
  /**
   * Lodash library.
   */
  private lodash: any;
  /**
   * Notification service.
   */
  private cheNotification: any;

  /**
   * New RAM value.
   */
  value: number;
  /**
   * Current account's id (set from outside).
   */
  accountId: string;
  /**
   * Callback controller (set from outside).
   */
  callbackController: any;
  /**
   * Provided free RAM. (set from outside).
   */
  freeRAM: number;
  /**
   * Provided total RAM. (set from outside).
   */
  totalRAM: number;
  /**
   * Price of the resources. Is retrieved from package details.
   */
  price: number;
  /**
   * Price for the resources by portion of time. Is retrieved from package details.
   */
  partialPrice: number;
  /**
   * Amount of resources, that are paid for. Is retrieved from package details.
   */
  amount: string;
  /**
   * Team workspaces idle timeout. Is retrieved from package details.
   */
  timeout: string;
  /**
   * The next month charge date.
   */
  nextMonthChargeDate: Date;
  /**
   * The number of days left till the end of current month.
   */
  leftDaysInMonth: number;
  /**
   * Minimum amount of resources, that can be bought. Is retrieved from package details.
   */
  minValue: number;
  /**
   * Maximum amount of resources, that can be bought. Is retrieved from package details.
   */
  maxValue: number;
  /**
   * Package with RAM type.
   */
  ramPackage: any;
  /**
   * Loading state of the dialog.
   */
  isLoading: boolean;
  /**
   * Steps to use them in dialog template.
   */
  step: Object;
  /**
   * Current step of wizard.
   */
  currentStep: number;
  /**
   * Credit card data.
   */
  creditCard: ICreditCard;

  /**
   * @ngInject for Dependency injection
   */
  constructor ($mdDialog: angular.material.IDialogService, $q: ng.IQService, codenvySubscription: CodenvySubscription,
               lodash: any, cheNotification: any, billingService: BillingService) {
    this.$q = $q;
    this.$mdDialog = $mdDialog;
    this.codenvySubscription = codenvySubscription;
    this.lodash = lodash;
    this.cheNotification = cheNotification;
    this.billingService = billingService;
    this.isLoading = true;
    this.step = Step;
    this.currentStep = Step.ONE;

    this.calcDateBasedValues();

    this.getPackages();
    this.fetchCreditCard();
  }

  /**
   * Calculate the needed values based on current date: next month charge date
   * and the number of days left in current month.
   */
  calcDateBasedValues(): void {
    let now = new Date();
    let month = now.getMonth();
    this.nextMonthChargeDate = (now.getMonth() == 11) ? new Date(now.getFullYear() + 1, 0, 1) : new Date(now.getFullYear(), now.getMonth() + 1, 1);
    this.leftDaysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate() - now.getDate() + 1;
  }



  /**
   * Fetches the list of packages.
   */
  getPackages(): void {
    this.isLoading = true;
    this.codenvySubscription.fetchPackages().then(() => {
      this.isLoading = false;
      this.processPackages(this.codenvySubscription.getPackages());
    }, (error: any) => {
      this.isLoading = false;
      if (error.status === 304) {
        this.processPackages(this.codenvySubscription.getPackages());
      }
    });
  }

  /**
   * Processes packages to get RAM resources details.
   *
   * @param packages list of packages
   */
  processPackages(packages: Array<any>): void {
    this.ramPackage = this.lodash.find(packages, (pack: any) => {
      return pack.type === CodenvyResourceLimits.RAM;
    });

    if (!this.ramPackage) {
      return;
    }

    let ramResource = this.lodash.find(this.ramPackage.resources, (resource: any) => {
      return resource.type === CodenvyResourceLimits.RAM;
    });

    let timeoutResource = this.lodash.find(this.ramPackage.resources, (resource: any) => {
      return resource.type === CodenvyResourceLimits.TIMEOUT;
    });

    if (!ramResource) {
      return;
    }

    this.price = ramResource.fullPrice;
    this.partialPrice = ramResource.partialPrice;
    this.amount = ramResource.amount / 1024 + 'GB';
    this.minValue = ramResource.minAmount / 1024;
    let paidRAM = this.totalRAM - this.freeRAM;
    this.maxValue = ramResource.maxAmount / 1024 - paidRAM;
    this.value = angular.copy(this.minValue);
    this.timeout = timeoutResource ? timeoutResource.amount / 60 : 4;
  }

  /**
   * Calculate the cost of the request per month.
   *
   * @returns {number} the request's cost per month
   */
  calcRequestMonthlyCost(): number {
    return this.price * this.value;
  }

  /**
   * Calculate the price that will be charged based on left days in month and chosen amount of resources.
   *
   * @returns {number} charged amount
   */
  calcChargedAmount(): number {
    return this.partialPrice * this.value * this.leftDaysInMonth;
  }

  /**
   * Calculate the price that will be charged based chosen amount of resources next month.
   *
   * @returns {number} charged amount
   */
  calcNextMonthChargeAmount(): number {
    return this.price * (this.value + this.totalRAM - this.freeRAM);
  }

  /**
   * Hides the dialog.
   */
  hide() {
    this.$mdDialog.hide();
  }

  /**
   * Requests more RAM based on subscription state.
   */
  getMoreRAM(): void {
    if (!this.creditCard && this.currentStep === Step.ONE) {
      this.currentStep = Step.TWO;
      return;
    }

    this.isLoading = true;

    let savePromise;
    if (!this.creditCard.token) {
      savePromise = this.saveCard();
    } else {
      let defer = this.$q.defer();
      savePromise = defer.promise;
      defer.resolve();
    }

    savePromise.then(() => {
      return this.codenvySubscription.fetchActiveSubscription(this.accountId).then(() => {
        this.processSubscription(this.codenvySubscription.getActiveSubscription(this.accountId));
      }, (error: any) => {
        this.processSubscription(this.codenvySubscription.getActiveSubscription(this.accountId));
      });
    }).finally(() => {
      this.isLoading = false;
    });
  }

  /**
   * Process active subscription if exists or creates new one,
   *
   * @param subscription
   */
  processSubscription(subscription: any): void {
    let ramValue = this.value * 1024;

    let promise;
    // check subscription exists:
    if (subscription) {
      let packages = angular.copy(subscription.packages);

      // try to update RAM package:
      let ramPackage = this.lodash.find(packages, (pckg: any) => {
        return pckg.templateId === this.ramPackage.id;
      });

      if (ramPackage) {
        let ramResource = this.lodash.find(ramPackage.resources, (resource: any) => {
          return resource.type === CodenvyResourceLimits.RAM;
        });
        // check RAM resource was defined:
        if (ramResource) {
          ramResource.amount += ramValue;
        } else { // process no RAM resource:
          ramPackage.resources.push(this.prepareRAMResource(ramValue));
        }
      } else { // process no RAM package:
        let resources = [this.prepareRAMResource(ramValue)];
        packages.push({resources: resources});
      }
      promise = this.codenvySubscription.updateSubscription(this.accountId, packages);
    } else { // process no active subscription:
      let packages = [];
      let resources = [this.prepareRAMResource(ramValue)];
      packages.push({resources: resources, templateId: this.ramPackage.id});
      promise = this.codenvySubscription.createSubscription(this.accountId, packages);
    }

    promise.then(() => {
      this.isLoading = false;
      this.callbackController.onRAMChanged();
      this.hide();
    }, (error: any) => {
      this.isLoading = false;
      this.cheNotification.showError(error.data && error.data.message ? error.data.message : 'Failed to add more RAM to account.');
    });
  }

  /**
   * Returns RAM resource based on provided RAM amount.
   *
   * @param value RAM amount
   * @returns any ram resource
   */
  prepareRAMResource(value: number): any {
    return {amount: value, unit: 'mb', type: CodenvyResourceLimits.RAM};
  }

  /**
   * Gets credit card.
   *
   * @return {ng.IPromise<any>}
   */
  fetchCreditCard(): ng.IPromise<any> {
    return this.billingService.fetchCreditCard(this.accountId).then((creditCard: ICreditCard) => {
      this.creditCard = creditCard;
    });
  }

  /**
   * Adds new credit card or updates an existing one.
   */
  saveCard(): ng.IPromise<any> {
    this.isLoading = true;

    return this.billingService.addCreditCard(this.accountId, this.creditCard).then(() => {
      return this.fetchCreditCard();
    }, (error: any) => {
      this.cheNotification.showError(error && error.data && error.data.message ? error.data.message : 'Failed to save the credit card.');
    }).finally(() => {
      this.isLoading = false;
    })
  }
}
