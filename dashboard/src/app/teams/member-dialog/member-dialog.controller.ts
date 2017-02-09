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
import {CodenvyUser} from '../../../components/api/codenvy-user.factory';
import {CodenvyTeam} from '../../../components/api/codenvy-team.factory';
import {CodenvyTeamRoles} from '../../../components/api/codenvy-team-roles';

/**
 * @ngdoc controller
 * @name teams.member:MemberDialogController
 * @description This class is handling the controller for adding/editing members dialog.
 * @author Ann Shumilova
 */
export class MemberDialogController {
  /**
   * Team API interaction.
   */
  private codenvyTeam: CodenvyTeam;
  /**
   * User API interaction.
   */
  private codenvyUser: CodenvyUser;
  /**
   * Service for displaying dialogs.
   */
  private $mdDialog: angular.material.IDialogService;
  /**
   * Promises service.
   */
  private $q: ng.IQService;
  /**
   * Lodash library.
   */
  private lodash: any;
  /**
   * Processing state of adding member.
   */
  private isProcessing: boolean;
  /**
   * Set of user roles info.
   */
  private roles: Array<any>;
  /**
   * Already added emails.
   */
  private emails: Array<string>;
  /**
   * Existing members.
   */
  private members: Array<any>;
  /**
   * Entered email address.
   */
  private email: string;
  /**
   * Controller that will handle callbacks.
   */
  private callbackController: any;

  /**
   * Member to be displayed, may be <code>null</code> if add new member is needed. (Comes from outside)
   */
  private member: any;

  /**
   * Role to be used, may be <code>null</code> if role is needed to be set. (Comes from outside)
   */
  private role: any;
  /**
   * Dialog window title.
   */
  private title: string;
  /**
   * Title of operation button (Save or Add)
   */
  private buttonTitle: string;
  /**
   * Email validation error message.
   */
  private emailError: string;

  /**
   * Default constructor that is using resource
   * @ngInject for Dependency injection
   */
  constructor($mdDialog: angular.material.IDialogService, codenvyTeam: CodenvyTeam, codenvyUser: CodenvyUser, $q: ng.IQService, lodash: any) {
    this.$mdDialog = $mdDialog;
    this.codenvyTeam = codenvyTeam;
    this.codenvyUser = codenvyUser;
    this.$q = $q;
    this.lodash = lodash;

    this.isProcessing = false;

    this.emails = [];
    this.members.forEach((member: any) => {
      this.emails.push(member.email);
    });

    // role is set, need to add only user with this role:
    if (this.role) {
      this.email = '';
      this.title = 'Add new ' + this.role.title.toLowerCase();
      this.buttonTitle = 'Add';
      return;
    }

    this.roles = [];
    if (this.member) {
      this.title = 'Edit ' + this.member.name + ' roles';
      this.buttonTitle = 'Save';
      this.email = this.member.email;
      let roles = this.codenvyTeam.getRolesFromActions(this.member.permissions.actions);
      this.roles.push({'role' : CodenvyTeamRoles.TEAM_MEMBER, 'allowed' : roles.indexOf(CodenvyTeamRoles.TEAM_MEMBER) >= 0});
      this.roles.push({'role' : CodenvyTeamRoles.TEAM_ADMIN, 'allowed' : roles.indexOf(CodenvyTeamRoles.TEAM_ADMIN) >= 0});
    } else {
      this.email = '';
      this.title = 'Invite member to collaborate';
      this.buttonTitle = 'Add';
      this.roles.push({'role' : CodenvyTeamRoles.TEAM_MEMBER, 'allowed' : true});
      this.roles.push({'role' : CodenvyTeamRoles.TEAM_ADMIN, 'allowed' : false});
    }
  }

  /**
   * Hides the add member dialog.
   */
  hide(): void {
    this.$mdDialog.hide();
  }

  /**
   * Checks whether entered email valid and is unique.
   *
   * @param value value with email(s) to check
   * @returns {boolean} true if pointed email(s) are valid and not in the list yet
   */
  isValidEmail(value: string): boolean {
    // return this.emails.indexOf(email) < 0;
    let emails = value.replace(/ /g, ',').split(',');
    for (let i = 0; i < emails.length; i++) {
      let email = emails[i];
      if (email.length > 0 && !/\S+@\S+\.\S+/.test(email)) {
        this.emailError = email + ' is invalid email address.';
        return false;
      }

      if (this.emails.indexOf(email) >= 0) {
        this.emailError = 'User with email ' + email + ' is already invited.';
        return false;
      }
    }
    return true;
  }

  /**
   * Adds new member.
   */
  addMembers(): void {
    let userRoles =  this.role ? [this.role] : this.getRoles();

    let emails = this.email.replace(/ /g, ',').split(',');
    // form the list of emails without duplicates and empty values:
    let resultEmails = emails.reduce((array: Array<string>, element: string) => {
      if (array.indexOf(element) < 0 && element.length > 0) {
        array.push(element);
      }
      return array;
    }, []);

    let promises = [];
    let users = [];
    resultEmails.forEach((email: string) => {
      promises.push(this.processUser(email, users));
    });

    this.$q.all(promises).then(() => {
      this.finishAdding(users, userRoles);
    });
  }

  processUser(email: string, users : Array<any>): ng.IPromise<any> {
    let deferred = this.$q.defer();
    let user = this.codenvyUser.getUserByAlias(email);
    if (user) {
      users.push(user);
      deferred.resolve();
    } else {
      user = {};
      user.email = email;
      this.isProcessing = true;
      this.codenvyUser.fetchUserByAlias(email).then(() => {
        users.push(this.codenvyUser.getUserByAlias(email));
        deferred.resolve();
      }, (error: any) => {
        users.push(user);
        deferred.resolve();
      });
    }
    return deferred.promise;
  }

  /**
   * Forms the list of user's roles by role's info from page.
   *
   * @returns {Array<any>} roles
   */
  getRoles(): Array<any> {
    let userRoles = [];
    this.roles.forEach((roleInfo: any) => {
      if (roleInfo.allowed) {
        userRoles.push(roleInfo.role);
      }
    });
    return userRoles;
  }

  /**
   * Handle edit member user's action.
   */
  editMember(): void {
    this.member.permissions.actions = this.getCurrentActions();
    this.callbackController.updateMember(this.member);
    this.hide();
  }

  /**
   * Returns the actions of current chosen roles.
   */
  getCurrentActions(): Array<string> {
    let userRoles = this.getRoles();
    let processedActions = [];
    this.roles.forEach((data: any) => {
      processedActions = processedActions.concat(data.role.actions);
    });


    let actions = this.member ? this.member.permissions.actions : [];
    let otherActions = this.lodash.difference(actions, processedActions);

    return this.lodash.uniq(this.codenvyTeam.getActionsFromRoles(userRoles).concat(otherActions));
  }

  /**
   * Finish adding user state.
   *
   * @param users users to be added
   * @param roles user's roles
   */
  finishAdding(users: Array<any>, roles: any): void {
    this.isProcessing = false;
    this.callbackController.addMembers(users, roles);
    this.hide();
  }

}
